package com.dat3m.dartagnan.program.atomic.event;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.BOpUn;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.utils.recursion.RecursiveFunction;
import com.dat3m.dartagnan.wmm.utils.Arch;

import java.util.List;

import static com.dat3m.dartagnan.expression.op.COpBin.EQ;
import static com.dat3m.dartagnan.expression.op.COpBin.NEQ;
import static com.dat3m.dartagnan.program.EventFactory.*;
import static com.dat3m.dartagnan.program.arch.aarch64.utils.Mo.*;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.SC;
import static com.dat3m.dartagnan.program.utils.EType.STRONG;
import static com.dat3m.dartagnan.wmm.utils.Arch.POWER;

public class AtomicCmpXchg extends AtomicAbstract implements RegWriter, RegReaderData {

    private final Register expected;

    public AtomicCmpXchg(Register register, IExpr address, Register expected, ExprInterface value, String mo, boolean strong) {
        super(address, register, value, mo);
        this.expected = expected;
        if(strong) {
        	addFilters(STRONG);
        }
    }

    public AtomicCmpXchg(Register register, IExpr address, Register expected, ExprInterface value, String mo) {
        this(register, address, expected, value, mo, false);
    }

    private AtomicCmpXchg(AtomicCmpXchg other){
        super(other);
        this.expected = other.expected;
    }

    //TODO: Override getDataRegs???

    @Override
    public String toString() {
    	String tag = is(STRONG) ? "_strong" : "_weak";
    	tag += mo != null ? "_explicit" : "";
        return resultRegister + " = atomic_compare_exchange" + tag + "(*" + address + ", " + expected + ", " + value + (mo != null ? ", " + mo : "") + ")";
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public AtomicCmpXchg getCopy(){
        return new AtomicCmpXchg(this);
    }


    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    protected RecursiveFunction<Integer> compileRecursive(Arch target, int nextId, Event predecessor, int depth) {
        //TODO: Perform Store on <expected> (in general, <expected> should be an address and not a register)
    	List<Event> events;
        switch(target) {
            case NONE:
            case TSO: {
                Register dummy = new Register(null, resultRegister.getThreadId(), resultRegister.getPrecision());
                Label casFail = newLabel("CAS_fail");
                Label casEnd = newLabel("CAS_end");
                Load load = newRMWLoad(dummy, address, mo);
                Local casCmpResult = newLocal(resultRegister, new Atom(dummy, EQ, expected));
                CondJump branchOnCasCmpResult = newJump(new Atom(resultRegister, NEQ, IConst.ONE), casFail);
                Store store = newRMWStore(load, address, value, mo);
                CondJump gotoCasEnd = newGoto(casEnd);
                Local updateReg = newLocal(expected, dummy);

                events = eventSequence(
                		// Indentation shows the branching structure
                        load,
                        casCmpResult,
                        branchOnCasCmpResult,
                        	store,
                        	gotoCasEnd,
                        casFail,
                        	updateReg,
                        casEnd
                );
                break;
            }
            case POWER:
            case ARM8: {
                String loadMo = extractLoadMo(mo);
                String storeMo = extractStoreMo(mo);

                Register dummy = new Register(null, resultRegister.getThreadId(), resultRegister.getPrecision());
                Label casFail = newLabel("CAS_fail");
                Label casEnd = newLabel("CAS_end");
                Load load = newRMWLoadExclusive(dummy, address, loadMo);
                Local casCmpResult = newLocal(resultRegister, new Atom(dummy, EQ, expected));
                CondJump branchOnCasCmpResult = newJump(new Atom(resultRegister, NEQ, IConst.ONE), casFail);
                // ---- CAS success ----
                Store store = newRMWStoreExclusive(address, value, storeMo, is(STRONG));
                ExecutionStatus optionalExecStatus = null;
                Local optionalUpdateCasCmpResult = null;
                if (!is(STRONG)) {
                    Register statusReg = new Register("status(" + getOId() + ")", resultRegister.getThreadId(), resultRegister.getPrecision());
                    optionalExecStatus = newExecutionStatus(statusReg, store);
                    optionalUpdateCasCmpResult = newLocal(resultRegister, new BExprUn(BOpUn.NOT, statusReg));
                }
                CondJump gotoCasEnd = newGoto(casEnd);
                // ---------------------
                // ---- CAS Fail ----
                Local updateExpected = newLocal(expected, dummy);
               
                // --- Add Fence before under POWER ---
                Fence optionalMemoryBarrier = null;
                Fence optionalISyncBarrier = (target.equals(POWER) && loadMo.equals(ACQ)) ? Power.newISyncBarrier() : null;
                if(target.equals(POWER)) {
                    optionalMemoryBarrier = mo.equals(SC) ? Power.newSyncBarrier()
                            : storeMo.equals(REL) ? Power.newLwSyncBarrier()
                            : null;
                }

                events = eventSequence(
                		// Indentation shows the branching structure
                        optionalMemoryBarrier,
                        load,
                        casCmpResult,
                        branchOnCasCmpResult,
                        	store,
                        	optionalExecStatus,
                        	optionalUpdateCasCmpResult,
                        	optionalISyncBarrier,
                        	gotoCasEnd,
                        casFail,
                        	updateExpected,
                        casEnd
                );

                break;
            }
            default:
                throw new UnsupportedOperationException("Compilation to " + target + " is not supported for " + this);
        }
        return compileSequenceRecursive(target, nextId, predecessor, events, depth + 1);
    }
}
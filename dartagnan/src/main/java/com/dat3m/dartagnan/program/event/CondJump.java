package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class CondJump extends Jump implements RegReaderData {

    private final BExpr expr;
    private final ImmutableSet<Register> dataRegs;

    public CondJump(BExpr expr, Label label){
        super(label);
        if(expr == null){
            throw new IllegalArgumentException("CondJump event requires non null expression");
        }
        this.expr = expr;
        dataRegs = expr.getRegs();
        addFilters(EType.BRANCH, EType.COND_JUMP, EType.REG_READER);
    }

    @Override
    public ImmutableSet<Register> getDataRegs(){
        return dataRegs;
    }

    @Override
    public String toString(){
        return "if(" + expr + "); then goto " + label;
    }


    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public int unroll(int bound, int nextId, Event predecessor) {
        if(label.getOId() < oId){
        	// Here comes a validate
    		if(true){
    			int currentBound = bound;

    			while(currentBound > 0){
    				Skip exitMainBranch = new Skip();
    				Skip exitElseBranch = new Skip();
					If ifEvent = new If(expr, exitMainBranch, exitElseBranch);
					ifEvent.oId = oId;
					ifEvent.uId = nextId++;
	
					predecessor.setSuccessor(ifEvent);
					predecessor = copyPath(label.successor, this, ifEvent, bound);
					predecessor.setSuccessor(exitMainBranch);
					exitMainBranch.setSuccessor(exitElseBranch);
					predecessor = exitElseBranch;
	
					nextId = ifEvent.successor.unroll(currentBound, nextId, ifEvent);
					currentBound--;
				}
	
				predecessor.setSuccessor(this.getSuccessor());
				if(predecessor.getSuccessor() != null){
					nextId = predecessor.getSuccessor().unroll(bound, nextId, predecessor);
				}
				return nextId;
    		}
            throw new UnsupportedOperationException("Malformed CondJump");
        }
        return super.unroll(bound, nextId, predecessor);
    }

    @Override
    public CondJump getCopy(int bound){
    	Label newLabel = new Label(label.getName() + "_" + bound);
    	return new CondJump(expr, newLabel);
        //throw new UnsupportedOperationException("Cloning is not implemented for CondJump event");
    }


    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public int compile(Arch target, int nextId, Event predecessor) {
        cId = nextId++;
        if(successor == null){
            throw new RuntimeException("Malformed CondJump event");
        }
        return successor.compile(target, nextId, this);
    }


    // Encoding
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public BoolExpr encodeCF(Context ctx, BoolExpr cond) {
        if(cfEnc == null){
            cfCond = (cfCond == null) ? cond : ctx.mkOr(cfCond, cond);
            BoolExpr ifCond = expr.toZ3Bool(this, ctx);
            label.addCfCond(ctx, ctx.mkAnd(ifCond, cfVar));
            cfEnc = ctx.mkAnd(ctx.mkEq(cfVar, cfCond), encodeExec(ctx));
            cfEnc = ctx.mkAnd(cfEnc, successor.encodeCF(ctx, ctx.mkAnd(ctx.mkNot(ifCond), cfVar)));
        }
        return cfEnc;
    }
}

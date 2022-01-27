package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;
import com.dat3m.dartagnan.program.memory.Address;
import org.sosy_lab.java_smt.api.SolverContext;

/**
 * An instance of this class is generated for each defined location in the shared memory of a program.
 * It is exposed to the memory consistency model as a visible event.
 * It acts like a regular store, such that load events may read from it and other stores may overwrite it.
 */
public class Init extends MemEvent {

	private final Address base;
	private final int offset;
	
	public Init(Address b, int o) {
		super(b.add(o), null);
		base = b;
		offset = o;
		addFilters(Tag.ANY, Tag.VISIBLE, Tag.MEMORY, Tag.WRITE, Tag.INIT);
	}

	/**
	 * Partially identifies this instance in the program.
	 * @return
	 * Address of the array whose field is initialized in this event.
	 */
	public Address getBase() {
		return base;
	}

	/**
	 * Partially identifies this instance in the program.
	 * @return
	 * Number of fields before the initialized location.
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Initial value at the associated field.
	 * @return
	 * Content of the location at the start of each execution.
	 */
	public IConst getValue(){
		return base.getInitialValue(offset);
	}

	@Override
	public void initializeEncoding(SolverContext ctx) {
		super.initializeEncoding(ctx);
		memValueExpr = getValue().toIntFormula(ctx);
	}

	@Override
	public String toString() {
		return String.format("%s[%d] := %s",base,offset,getValue());
	}

	@Override
	public IConst getMemValue(){
		return getValue();
	}

	// Visitor
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public <T> T accept(EventVisitor<T> visitor) {
		return visitor.visitInit(this);
	}
}
package com.dat3m.dartagnan.program.processing.compilation;

import com.dat3m.dartagnan.program.event.EventFactory;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.rmw.RMWStoreExclusive;
import com.dat3m.dartagnan.program.event.core.rmw.StoreExclusive;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;

import static com.dat3m.dartagnan.program.event.EventFactory.eventSequence;
import static com.dat3m.dartagnan.program.event.EventFactory.newExecutionStatusWithDependencyTracking;
import java.util.List;

class VisitorRISCV extends VisitorBase implements EventVisitor<List<Event>> {

	protected VisitorRISCV() {}
	
	@Override
	public List<Event> visitStoreExclusive(StoreExclusive e) {
        RMWStoreExclusive store = EventFactory.RISCV.newRMWStoreConditional(e.getAddress(), e.getMemValue(), e.getMo());

        return eventSequence(
                store,
                newExecutionStatusWithDependencyTracking(e.getResultRegister(), store)
        );
	}

}
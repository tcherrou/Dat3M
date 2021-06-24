package com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.stat;

import com.dat3m.dartagnan.analysis.graphRefinement.coreReason.CoreLiteral;
import com.dat3m.dartagnan.analysis.graphRefinement.coreReason.ReasoningEngine;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.AbstractEventGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.EventGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.Conjunction;
import com.dat3m.dartagnan.analysis.graphRefinement.util.EdgeDirection;
import com.dat3m.dartagnan.utils.timeable.Timestamp;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.ExecutionModel;

import java.util.Collections;
import java.util.List;

public abstract class StaticEventGraph extends AbstractEventGraph {
    protected int size;
    private VerificationTask task;

    @Override
    public List<EventGraph> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Timestamp getTime(Edge edge) {
        return contains(edge) ? Timestamp.ZERO : Timestamp.INVALID;
    }

    @Override
    public Timestamp getTime(EventData a, EventData b) {
        return contains(a, b) ? Timestamp.ZERO : Timestamp.INVALID;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int getMinSize() { return size; }

    @Override
    public int getMaxSize() { return size; }

    @Override
    public int getEstimatedSize() { return size; }

    @Override
    public abstract int getMinSize(EventData e, EdgeDirection dir);

    @Override
    public int getMaxSize(EventData e, EdgeDirection dir) {
        return getMinSize(e, dir);
    }

    @Override
    public int getEstimatedSize(EventData e, EdgeDirection dir) {
        return getMinSize(e, dir);
    }

    @Override
    public void backtrack() {
    }

    @Override
    public void constructFromModel(ExecutionModel context) {
        super.constructFromModel(context);
        this.task = context.getVerificationTask();
        size = 0;
    }

    @Override
    public Conjunction<CoreLiteral> computeReason(Edge edge, ReasoningEngine reasEngine) {
        if (!contains(edge))
            return Conjunction.FALSE;

        return reasEngine.getExecReason(edge);

        /*BranchEquivalence eq = task.getBranchEquivalence();
        Event e1 = eq.getRepresentative(edge.getFirst().getEvent());
        Event e2 = eq.getRepresentative(edge.getSecond().getEvent());
        if (eq.isImplied(e1, e2)) {
            return new Conjunction<>(new EventLiteral(context.getData(e1)));
        } else if (eq.isImplied(e2, e1)) {
            return new Conjunction<>(new EventLiteral(context.getData(e2)));
        } else {
            return new Conjunction<>(new EventLiteral(context.getData(e1)), new EventLiteral(context.getData(e2)));
        }*/

        //return new Conjunction<>(new EventLiteral(edge.getFirst()), new EventLiteral(edge.getSecond()));
    }


    protected final void autoComputeSize() {
        size = 0;
        for (EventData e : context.getEventList())
            size += getMinSize(e, EdgeDirection.Outgoing);
    }

}

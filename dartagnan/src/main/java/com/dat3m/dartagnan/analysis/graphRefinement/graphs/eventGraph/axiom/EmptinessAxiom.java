package com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.axiom;

import com.dat3m.dartagnan.analysis.graphRefinement.coreReason.CoreLiteral;
import com.dat3m.dartagnan.analysis.graphRefinement.coreReason.ReasoningEngine;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.EventGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.Conjunction;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.DNF;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.SortedClauseSet;
import com.dat3m.dartagnan.utils.timeable.Timeable;
import com.dat3m.dartagnan.verification.model.Edge;
import com.dat3m.dartagnan.verification.model.ExecutionModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EmptinessAxiom extends GraphAxiom {

    private final List<Edge> violatingEdges = new ArrayList<>();

    public EmptinessAxiom(EventGraph inner) {
        super(inner);
    }

    @Override
    public void onGraphChanged(EventGraph changedGraph, Collection<Edge> addedEdges) {
        // The check is superfluous but we keep it for now
        if (changedGraph == inner) {
            violatingEdges.addAll(addedEdges);
        }
    }

    @Override
    public void initialize(ExecutionModel context) {
        super.initialize(context);
        violatingEdges.clear();
        onGraphChanged(inner, inner);
    }

    @Override
    public void backtrack() {
        violatingEdges.removeIf(Timeable::isInvalid);
    }

    @Override
    public void clearViolations() {
        violatingEdges.clear();
    }

    @Override
    public boolean checkForViolations() {
        return !violatingEdges.isEmpty();
    }

    @Override
    public DNF<CoreLiteral> computeReasons(ReasoningEngine reasEng) {
        SortedClauseSet<CoreLiteral> clauseSet = new SortedClauseSet<>();
        for (Edge e : violatingEdges) {
            clauseSet.add(inner.computeReason(e, reasEng));
        }
        clauseSet.simplify();
        return new DNF<>(clauseSet.getClauses());
    }

    @Override
    public Conjunction<CoreLiteral> computeSomeReason(ReasoningEngine reasEng) {
        return checkForViolations() ? inner.computeReason(violatingEdges.get(0), reasEng) : Conjunction.FALSE;
    }
}
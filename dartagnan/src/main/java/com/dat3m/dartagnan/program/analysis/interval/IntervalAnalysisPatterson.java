package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.expression.integers.*;
import com.dat3m.dartagnan.program.analysis.BackwardsReachingDefinitionsAnalysis;
import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.program.analysis.ReachingDefinitionsAnalysis;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;
import com.dat3m.dartagnan.program.event.core.threading.ThreadArgument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.expression.integers.IntSizeCast;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.core.*;
import org.sosy_lab.common.configuration.Configuration;

import java.util.*;
import java.util.stream.Collectors;

/*
 * Forward Interval analysis
 * Computes the intervals of registers in the program.
 * We assume that the program has been fully processed and contains no loops and functions are inlined.
 * Algorithm based on Patterson's worklist algorithm.
 * An interval is of the form [lb,ub] where a variable can take any possible value between (and including) lb and ub.
 */
public class IntervalAnalysisPatterson implements IntervalAnalysis {

    Collection<Register> allRegisters;
    private Program program;
    private VerificationTask task;
    private Thread currentThread;
    private Event finalEvent;
    private final Queue<Event> dataflowWorkList = new LinkedList<>();
    Map<Event,Map<Register,Interval>> eventToIntervals = new HashMap<>();

    private RelationAnalysis relationAnalysis;
    private AliasAnalysis aliasAnalysis;
    // Dependence on BackwardReachingDefinitionsAnalysis for the "getReaders()" method.
    private BackwardsReachingDefinitionsAnalysis reachingDefinitionsAnalysis;

    public void computeAnalysisMetrics() {
        // Iterate over all registers
        // For each register check if their bound is reduced
        // numbers such as reduced and not reduced and print at the end
        double regReduced = 0.0;
        double regTop = 0.0;
        double regTotal = allRegisters.size();
        Map<Register,Interval> reducedRegisters = new HashMap<>();
        logger.debug("Computing regular interval metrics for thread: {}", currentThread);
        for(Register r : allRegisters) {
            Interval interval = eventToIntervals.get(finalEvent).get(r);
            if (interval != null) {
                if(interval.isTop(  r.getType())) regTop++; else {regReduced++; reducedRegisters.put(r,interval);}
            } else {
                regTotal--;
            }
        }
        System.out.println("==============Interval Analysis Summary====================");
        System.out.println(currentThread);
        System.out.println("#Registers: " + regTotal);
        System.out.println("#Bounds reduced: " + regReduced);
        System.out.println("#Bounds top: " + regTop);
        System.out.println("Registers reduced:  "+ reducedRegisters);
        System.out.println("==============Interval Analysis Summary End====================");
    }

    // For debugging
    private void logIntervals() {
        for(Thread t : program.getThreads()) {
            String header = String.format("Intervals of thread: %s\n", t.getName());
            System.out.println(header);
            System.out.println("===================================\n");
            for (Event e : t.getEvents()) {
                String eventFormat = String.format("%s=%s\n",e,eventToIntervals.get(e));
                System.out.println(eventFormat);
            }
            System.out.println("===================================\n");
        }
    }

    // Helper class to carry information about a register and its computed interval.

    static class IntervalInfo {
        public Register reg;
        public Interval interval;

        public IntervalInfo(Register reg, Interval interval) {
            this.reg = reg;
            this.interval = interval;
        }

        @Override
        public String toString() {
            return "IntervalInfo{" +
                    "reg=" + reg +
                    ", interval=" + interval +
                    '}';
        }
    }

    static Logger logger = LogManager.getLogger(IntervalAnalysis.class);

    public static IntervalAnalysis fromConfig(Program program, Context analysisContext, VerificationTask task,Configuration config) {
        IntervalAnalysisPatterson analysis = new IntervalAnalysisPatterson();
	    analysis.relationAnalysis = analysisContext.get(RelationAnalysis.class);
	    analysis.aliasAnalysis = analysisContext.get(AliasAnalysis.class);
        analysis.reachingDefinitionsAnalysis =(BackwardsReachingDefinitionsAnalysis) analysisContext.get(ReachingDefinitionsAnalysis.class);
	    analysis.task = task;
        analysis.program = program;
        analysis.computeIntervalsPatterson(program);
       // analysis.logIntervals();
        return analysis;
    }

    @Override
    public Interval getIntervalAt(Event event, Register r) {
        return eventToIntervals.getOrDefault(event, Collections.emptyMap()).get(r);
    }

    private void computeIntervalsPatterson(Program program) {

        for(Thread thread : program.getThreads()) {
            if(!(thread.getEntry().getSuccessor() instanceof Init)) {
                currentThread = thread;
                allRegisters = thread.getRegisters();
                computeIntervalsPatterson(thread);
                // computeAnalysisMetrics();
            }
        }
	// Whole program
	processDataFlow(dataflowWorkList);
    }

    private Map<Register,Interval> initialiseIntervals(Function f) {
        Map<Register,Interval> intervals = new HashMap<>();
        for(Register r : f.getRegisters()) {
            intervals.put(r,Interval.getTop(r.getType()));
        }
        return intervals;
    }

    private void computeIntervalsPatterson(Function function) {
        Map<Register,Interval> intervals = initialiseIntervals(function);
        //System.out.println(intervals);
        Queue<Event> flowList = new LinkedList<>();
        flowList.add(function.getEntry().getSuccessor());
        eventToIntervals.put(function.getEntry().getSuccessor(),new HashMap<>());
        processControlFlow(flowList);


    }

    private Map<Register, Interval> joinIntervals(Map<Register, Interval> prevIntervals, Map<Register, Interval> currentIntervals) {

        Map<Register,Interval> lessIntervals = (prevIntervals.size() <= currentIntervals.size()) ?
                new HashMap<>(prevIntervals) :
                new HashMap<>(currentIntervals);
        Map<Register,Interval> moreIntervals = (prevIntervals.size() > currentIntervals.size()) ?
                new HashMap<>(prevIntervals) :
                new HashMap<>(currentIntervals);


        for(var pair : lessIntervals.entrySet()) {
            Register key = pair.getKey();
            if(moreIntervals.containsKey(key)) {
                // Join same registers
                lessIntervals.replace(key,pair.getValue().join(moreIntervals.get(key)));
                moreIntervals.remove(key);
            }
        }
        // Add remaining registers
        lessIntervals.putAll(moreIntervals);
        return lessIntervals;
    }


    private Interval getInterval(Register r,Expression expr,Map<Register,Interval> prevIntervals) {
        Interval interval = null;
        if(expr instanceof IntLiteral lit) {
            interval = Interval.makeDefault(lit.getValueAsInt());
        } else if (expr instanceof Register reg) {
            interval = prevIntervals.getOrDefault(reg,Interval.getTop(reg.getType()));
        }
        if (interval == null) {
            return Interval.getTop(  r.getType());
        } else return interval;

    }





    private Interval evaluateExpressionToInterval(Register register,Expression expr, Map<Register,Interval> prevIntervals) {
        if(expr instanceof IntLiteral lit) {
            return Interval.makeDefault(lit.getValueAsInt());
        } else if (expr instanceof Register reg) {
            return prevIntervals.getOrDefault(reg, Interval.getTop(reg.getType()));
        } else if (expr instanceof IntSizeCast cast) {
            return evaluateExpressionToInterval(register,cast.getOperand(),prevIntervals);
        } else if (expr instanceof IntBinaryExpr binExpr){
            IntBinaryOp op = binExpr.getKind();
            Interval interval = evaluateExpressionToInterval(register,binExpr.getLeft(),prevIntervals);
            // TODO: Support more operations such as comparisons
            return interval.applyOperator(op,getInterval(register,binExpr.getRight(),prevIntervals),  register.getType());
        }

        return Interval.getTop(register.getType());
    }


    private Interval.IntervalPair calculateRestriction(Register reg, IntCmpOp op, Expression restrictingExpr, Map<Register,Interval> prevIntervals) {
        Interval resultingInterval = evaluateExpressionToInterval(reg,restrictingExpr,prevIntervals);
        // TODO: Deal with comparisons
        Interval registerInterval = getInterval(reg,reg,prevIntervals);
        return registerInterval.evaluateComparison(op,resultingInterval);
    }

    // Use the Relation Analysis to calculate the possible store from which a load can read from.
    private Set<Store> getPotentialStores(Load event) {
        return (relationAnalysis.getKnowledge(task.getMemoryModel()
                .getRelation(RF))
                .getMaySet()
                .getInMap()
                .get(event))
                .stream()
                .map(e -> (Store) e)
		        .collect(Collectors.toSet());
    }

    // Get loads that a store may influence.
    private Set<Load> getPotentialLoads(Store s) {
	    Set<Load> loads = new HashSet<>();
	    List<MemoryCoreEvent> memEvents = program.getThreadEvents(MemoryCoreEvent.class);
	    for (MemoryCoreEvent m : memEvents) {	
		    if(m instanceof Load l && aliasAnalysis.mayAlias(s,l)) loads.add(l);
	    }
	    return loads;


    }


    // Calculate the interval of a memory address.
    // Takes into account all stores from a load can read from.
    // Join the intervals of all possible stores
    private Interval calculatePossibleInterval(Set<Store> stores, Register r) {
        Interval interval = null;
            for (Store s : stores) {
                Map<Register,Interval> prevIntervals = eventToIntervals.getOrDefault(s,new HashMap<>());
                Expression value = s.getMemValue();
                Interval newInterval = evaluateExpressionToInterval(r,value,prevIntervals);
                interval = interval == null ? newInterval : interval.join(newInterval);
            }
        return interval;
    }


    // Analyse an event to calculate an interval for a register.
    private IntervalInfo analyseEvent(Event e) {
	    Map<Register,Interval> prevIntervals = eventToIntervals.get(e);
	    IntervalInfo info = null;
	    if (e instanceof RegWriter rw) {
		    if(rw instanceof Local lc) {
			    Register result = lc.getResultRegister();
			    Expression exp = lc.getExpr();
                info = new IntervalInfo(result,evaluateExpressionToInterval(result,exp,prevIntervals));
		    } if (rw instanceof Load ld) {
			    Set<Store> stores = getPotentialStores(ld);
			    Interval interval = calculatePossibleInterval(stores,rw.getResultRegister());
			    info = new IntervalInfo(ld.getResultRegister(), interval);
		    }
		    if (rw instanceof ThreadArgument ta) {
			    Expression arg = ta.getCreator().getArguments().get(ta.getIndex());
			    if (arg instanceof IntLiteral lit) {
				    Register result = ta.getResultRegister();
				    info = new IntervalInfo(result,evaluateExpressionToInterval(result,lit,prevIntervals));
			    }
		    }
	    }
	    return info;
    }


    private void addReadersToDataFlowList(RegWriter rw) {
	    reachingDefinitionsAnalysis.getReaders(rw).getReaders().forEach(x -> {
		    if (!dataflowWorkList.contains(x)) dataflowWorkList.add(x);
	    });
    }

    // Process the control flow of a thread.
    private void processControlFlow(Queue<Event> flowList) {
	while(!flowList.isEmpty()) {
            Event current = flowList.remove();
            Map<Register,Interval> prevIntervals = new HashMap<>(eventToIntervals.get(current));
            IntervalInfo info = analyseEvent(current);
            if (info != null) {
		if(current instanceof RegWriter rw) {
			addReadersToDataFlowList(rw);

		}
		updateIntervals(current,info,prevIntervals);
            }
            Map<Register,Interval> currentIntervals = eventToIntervals.get(current);
            // TODO: Review all the paths
            if (current instanceof CondJump cj) {
                Label l = cj.getLabel();
                Map<Register,Interval> labelIntervals = eventToIntervals.getOrDefault(l,new HashMap<>());
                // Unconditional jump
                if(cj.isGoto()) {
                    if (!flowList.contains(cj)) flowList.add(l);
                    eventToIntervals.put(l,joinIntervals(currentIntervals,labelIntervals));
                } else {
                    // Conditional jump can take two paths
                    Event successor = cj.getSuccessor();
                    if (!flowList.contains(l)) flowList.add(l);
                    if (!flowList.contains(successor)) flowList.add(successor);
                    Map<Register,Interval> successorIntervals = eventToIntervals.getOrDefault(successor,new HashMap<>());
                    // TODO: Revise or remove this part
                    if (cj.getGuard() instanceof IntCmpExpr cmp) {
                        Interval.IntervalPair pair= null;
                        Expression left = cmp.getLeft();
                        Expression right = cmp.getRight();
                        if(left instanceof Register r) {
                            pair = calculateRestriction(r,cmp.getKind(),right,currentIntervals);
                            if(pair != null) pair.reg = r;
                        } else if (left instanceof IntSizeCast c) {
                            Expression operand = c.getOperand();
                            if (operand instanceof Register r) {
                                pair = calculateRestriction(r,cmp.getKind(),right,currentIntervals);
                                if(pair != null) pair.reg = r;
                            }
                        }

                        if(pair != null) {
                            Map<Register,Interval>  trueIntervals = new HashMap<>(currentIntervals);
                            Map<Register,Interval>  falseIntervals = new HashMap<>(currentIntervals);
                            Register r = pair.reg;
                            trueIntervals.put(r,pair.left);
                            falseIntervals.put(r,pair.right);
                            eventToIntervals.put(l,joinIntervals(trueIntervals,labelIntervals));
                            eventToIntervals.put(successor,joinIntervals(falseIntervals,successorIntervals));
                        } else {
                            eventToIntervals.put(l,joinIntervals(currentIntervals,labelIntervals));
                            eventToIntervals.put(successor,joinIntervals(currentIntervals,successorIntervals));

                        }


                    }

                    eventToIntervals.put(l,joinIntervals(currentIntervals,labelIntervals));
                    eventToIntervals.put(successor,joinIntervals(currentIntervals,successorIntervals));
                }
            } else {
                Event successor = current.getSuccessor();


                if(successor != null) {
                    if (!flowList.contains(successor)) flowList.add(successor);

                    Map<Register,Interval> successorIntervals = eventToIntervals.getOrDefault(successor,new HashMap<>());
                    eventToIntervals.put(successor,joinIntervals(currentIntervals,successorIntervals));
                }
                else  {
                    finalEvent = current;
                }
            }

	}

    }


    private void updateIntervals(Event current,IntervalInfo info, Map<Register,Interval> prevIntervals) {
		prevIntervals.put(info.reg,info.interval);
                eventToIntervals.put(current,prevIntervals);
    }

    private void propagateNewIntervalToEvents(Collection<? extends Event> events,IntervalInfo info) {
        for(Event event : events) {
            Map<Register,Interval> newIntervals = new HashMap<>(eventToIntervals.getOrDefault(event,new HashMap<>()));
            newIntervals.replace(info.reg,info.interval);
            eventToIntervals.replace(event,newIntervals);

        }
    }

    // Process dataflow of the whole program (mostly to deal with load and stores).
    private void processDataFlow(Queue<Event> dataflowList) {
	    while(!dataflowList.isEmpty()) {
		    IntervalInfo info;
		    Event current = dataflowList.remove();
		    Map<Register,Interval> prevIntervals = new HashMap<>(eventToIntervals.get(current));
		    if (current instanceof RegWriter rw) {
			    info = analyseEvent(current);
			    Interval oldInterval = prevIntervals.get(rw.getResultRegister());
			    if (info != null && oldInterval != null) {
				    if (!oldInterval.equals(info.interval)) {
					    addReadersToDataFlowList(rw);
					    updateIntervals(current,info,prevIntervals);
                        propagateNewIntervalToEvents(reachingDefinitionsAnalysis.getReaders(rw).getReaders(),info);
				    }
			    } 
		    } else if (current instanceof Store s) {
			    Set<Load> loads = getPotentialLoads(s);
			    dataflowWorkList.addAll(loads);
		    }



	    }
    }



}

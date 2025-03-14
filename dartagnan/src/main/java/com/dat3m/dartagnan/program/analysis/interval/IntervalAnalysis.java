package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.dat3m.dartagnan.utils.Utils;



import com.dat3m.dartagnan.program.analysis.ReachingDefinitionsAnalysis;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.expression.BinaryExpression;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.integers.IntBinaryExpr;
import com.dat3m.dartagnan.expression.integers.IntBinaryOp;
import com.dat3m.dartagnan.expression.integers.IntLiteral;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.RegReader;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.metadata.MemoryOrder;
import org.sosy_lab.common.configuration.Configuration;

import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
/*
 * Forward Interval analysis
 * Computes the intervals of variables in the program.
 * An interval is of the form [lb,ub] where a variable can take any possible value between (and including) lb and ub.
 */
public class IntervalAnalysis {


    Map<Event,Map<Register,Interval>> eventToIntervals = new HashMap<>();
    public Map<Register,Interval> finalIntervals = new HashMap();

    private AliasAnalysis aliasAnalysis;
    public Map<Integer,Map<String,Interval>> getIntervalMap() {
        Map<Integer,Map<Register,Interval>> idsToIntervalMaps = transformKeys(eventToIntervals, Event::getGlobalId);
        Map<Integer,Map<String,Interval>> idsToIntervalMapsString = new HashMap<>();
        for (var entry : idsToIntervalMaps.entrySet()) {
            idsToIntervalMapsString.put(entry.getKey(), transformKeys(entry.getValue(),Register::getName));
        }

        return idsToIntervalMapsString;
    }

    private <T,C,M> Map<T,M> transformKeys(Map<C,M> registerIntervalMap, java.util.function.Function<C,T> transformer) {
        Map<T,M> result = new HashMap<>();
        for (var entry : registerIntervalMap.entrySet()) {
            M value = entry.getValue();
            result.put(transformer.apply(entry.getKey()), value);
        }
        return result;
    }


    class IntervalInfo {
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

    private IntervalInfo addIntInterval(Register register, IntLiteral lit) {
        Interval interval = Interval.makeDefault(lit.getValueAsInt());
	return new IntervalInfo(register,interval);
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
		   Interval interval = pair.getValue();
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

    private Map<Register,Interval> joinAtLabel(Label label, Map<Register,Interval> prevIntervals) {
	    Set<CondJump> jumps = label.getJumpSet();
	    Map<Register,Interval> joinedIntervals = new HashMap<>(prevIntervals);
	    for(CondJump jump : jumps) {
		    Map<Register,Interval> jumpIntervals = eventToIntervals.get(jump);
            jumpIntervals.forEach((reg,interval) -> {
                if(!joinedIntervals.containsKey(reg)) {
                    joinedIntervals.put(reg,interval);
                } else {
                    Interval oldInterval = joinedIntervals.get(reg);
                    joinedIntervals.replace(reg,oldInterval.join(interval));
                }
            });
	    }
	    return joinedIntervals;


    }


    private Interval getInterval(Expression expr,Map<Register,Interval> prevIntervals) {
        Interval interval = null;;
        if(expr instanceof IntLiteral lit) {
            interval = Interval.makeDefault(lit.getValueAsInt());
        } else if (expr instanceof Register reg) {
            interval = prevIntervals.getOrDefault(reg,Interval.getTop());
        }
        if (interval == null) {
            return Interval.getTop();
        } else return interval;

    }

    private IntervalInfo addOperatorExprRec(Register register, Expression expr,Map<Register,Interval> prevIntervals) {
	IntervalInfo info = null;

        if(expr instanceof IntLiteral lit) {

            return addIntInterval(register,lit);
        } else if (expr instanceof Register reg) {
            Interval interval = prevIntervals.getOrDefault(reg,Interval.getTop());
            return new IntervalInfo(register,interval);
        } else if (expr instanceof IntBinaryExpr binExpr){
            IntBinaryOp op = binExpr.getKind();
            info = addOperatorExprRec(register,binExpr.getLeft(),prevIntervals);
            // TODO: Support more operations
            Interval newInterval = info.interval.applyOperator(op,getInterval(binExpr.getRight(),prevIntervals));
	        info.interval = newInterval;
            return info;
        }

	return new IntervalInfo(register,Interval.getTop());

    }

    private IntervalInfo computeExpressionInterval(Register register,Expression expr,Map<Register,Interval> prevIntervals) {
        IntervalInfo info = null;
        if(expr instanceof IntLiteral lit) {
           info=addIntInterval(register,lit);
        } else if (expr instanceof  IntBinaryExpr binExpr) {
            info=addOperatorExprRec(register,binExpr,prevIntervals);
        }
    return info;
    }



    static Logger logger = LogManager.getLogger(IntervalAnalysis.class);

    public static IntervalAnalysis fromConfigPatterson(Program program, Context analysisContext, Configuration config) {
        IntervalAnalysis analysis = new IntervalAnalysis();
        analysis.aliasAnalysis = analysisContext.get(AliasAnalysis.class);
        long t0 = System.currentTimeMillis();
        analysis.computeIntervalsPatterson(program);
	long t1 = System.currentTimeMillis();
        logger.info("Finished interval analysis in {}", Utils.toTimeString(t1 - t0));
        logger.info(analysis.eventToIntervals);
        return analysis;

   }



    private void computeIntervalsPatterson(Program program) {
        for(Thread thread : program.getThreads()) {
            computeIntervalsPatterson(thread);
        }
    }

	//TODO:  Documentation and credits
private void computeIntervalsPatterson(Function function) {
	
	Queue<Event> flowList = new LinkedList();
	flowList.add(function.getEntry().getSuccessor());
	eventToIntervals.put(function.getEntry().getSuccessor(),new HashMap());
        while(!flowList.isEmpty()) {
	    Event current = flowList.remove();
            Map<Register,Interval> prevIntervals = eventToIntervals.get(current);
	        IntervalInfo info = null;
             if (current instanceof RegWriter rw) {

                 if(rw instanceof Local lc) {
		     Register result = lc.getResultRegister();
		     Expression exp = lc.getExpr();
                     if (exp instanceof IntLiteral lit){
                         info = addOperatorExprRec(result,lit,prevIntervals);
		     } else if (exp instanceof IntBinaryExpr binExpr) {
                         info = addOperatorExprRec(result,binExpr,prevIntervals);

		     } else if (exp instanceof Register r) {
			     info = addOperatorExprRec(result,r,prevIntervals);
		     }
		 } if (rw instanceof Load ld) {
			 info = new IntervalInfo(ld.getResultRegister(), Interval.getTop());
		 }


	     }


	     if (info != null) {
		     prevIntervals.put(info.reg,info.interval);
             eventToIntervals.put(current,prevIntervals);
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
	     }

        }

    }


}

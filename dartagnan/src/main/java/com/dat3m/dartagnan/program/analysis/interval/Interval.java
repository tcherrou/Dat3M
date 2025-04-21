package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.expression.integers.IntBinaryOp;
import com.dat3m.dartagnan.expression.integers.IntCmpOp;
import com.dat3m.dartagnan.program.Register;
import com.google.common.base.Preconditions;

import java.util.function.IntBinaryOperator;

public class Interval {
    public int lowerBound;
    public int upperBound;

    public Interval(int lowerBound, int upperBound) {
        Preconditions.checkArgument(lowerBound<=upperBound);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    // Class for dividing up intervals for conditional branhces
    public class IntervalPair {
        Interval left;
        Interval right;
	Register reg = null;

        public IntervalPair(Interval left,Interval right) {
            this.left= left;
            this.right= right;
        }

        @Override
        public String toString() {
            return "IntervalPair{" +
                    "left=" + left +
                    ", right=" + right +
                    ", Reg=" + reg +
                    '}';
        }
    }

    public static Interval getTop() {
        return new Interval(Integer.MIN_VALUE,Integer.MAX_VALUE);
    }


    // FIXME: better name needed
    public static Interval makeDefault(int value) {
        return new Interval(value,value);
    }

    public Interval join(Interval interval2) {
        return new Interval(Integer.min(this.lowerBound,interval2.lowerBound),Integer.max(this.upperBound,interval2.upperBound));    }

    public boolean isTop() {
	    return this.equals(Interval.getTop());
    }

    public Interval add(Interval interval2) {
        final int newLowerBound = this.lowerBound+interval2.lowerBound;
        final int newUpperBound = this.upperBound+interval2.upperBound;
        return new Interval(newLowerBound,newUpperBound);
    }


    public IntBinaryOperator selectOperator(IntBinaryOp op) {
        return switch (op) {
            case ADD -> Integer::sum;
            case SUB -> (x, y) -> x - y;
            case MUL -> (x, y) -> x * y;
            default -> null;
        };
    }
    public Interval applyOperator(IntBinaryOp op, Interval interval) {
	    Interval newInterval;
       	IntBinaryOperator opFunc = selectOperator(op);     
            if(opFunc != null && !this.isTop() && !interval.isTop()) {
                int resultBoundLowerBounds = opFunc.applyAsInt(this.lowerBound, interval.lowerBound);
                int resultBoundUpperBounds = opFunc.applyAsInt(this.upperBound, interval.upperBound);
                // The lower and upper bounds may switch depending on the operation (like MUL).
                int newLowerBound = Math.min(resultBoundLowerBounds,resultBoundUpperBounds);
                int newUpperBound = Math.max(resultBoundLowerBounds,resultBoundUpperBounds);

                newInterval = new Interval(newLowerBound,newUpperBound);

            } else {
                newInterval = Interval.getTop();
            }
	return newInterval;
    }

    public IntervalPair evaluateComparison(IntCmpOp op, Interval interval) {
        IntervalPair pair = null;
        switch (op) {
            case EQ -> pair = new IntervalPair(interval,this);
            case NEQ -> pair = new IntervalPair(this,interval);
        }
        return pair;
    }
    @Override 
    public boolean equals(Object other) {
	    if(other == null) {
		    return false;
	    }
	    if (other.getClass() != this.getClass()) {
		    return false;
	    }
	    final Interval otherInterval = (Interval) other;
	    return (otherInterval.lowerBound == this.lowerBound && otherInterval.upperBound == this.upperBound);
    }

    @Override
    public String toString() {
        return "[ " + this.lowerBound + ", " + this.upperBound + " ]";
    }
}

package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.integers.IntBinaryOp;
import com.dat3m.dartagnan.expression.integers.IntCmpOp;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.program.Register;
import com.google.common.base.Preconditions;

import java.util.function.LongBinaryOperator;

public class Interval {
    public long lowerBound;
    public long upperBound;

    public Interval(long lowerBound, long upperBound) {
        Preconditions.checkArgument(lowerBound<=upperBound);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    // Class for dividing up intervals for conditional branhces
    // Used for context-sensitive interval analysis.
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

    public static Interval getTop(Type type) {
        if(type instanceof IntegerType itype) {
            return new Interval(itype.getMinimumValue(true).longValue(),itype.getMaximumValue(true).longValue());
        } else
        if(type instanceof BooleanType) {
            return new Interval(0,1);
        }
        else throw new RuntimeException("Unsupported type: " + type);

    }


    // FIXME: better name needed
    public static Interval makeDefault(int value) {
        return new Interval(value,value);
    }

    public Interval join(Interval interval2) {
        return new Interval(Long.min(this.lowerBound,interval2.lowerBound),Long.max(this.upperBound,interval2.upperBound));    }

    public boolean isTop(Type type) {
	    return this.equals(Interval.getTop(type));
    }

    public Interval add(Interval interval2) {
        final long newLowerBound = this.lowerBound+interval2.lowerBound;
        final long newUpperBound = this.upperBound+interval2.upperBound;
        return new Interval(newLowerBound,newUpperBound);
    }


    public LongBinaryOperator selectOperator(IntBinaryOp op) {
        return switch (op) {
            case ADD -> Long::sum;
            case SUB -> (x, y) -> x - y;
            case MUL -> (x, y) -> x * y;
            default -> null;
        };
    }
    public Interval applyOperator(IntBinaryOp op, Interval interval,Type type) {
	    Interval newInterval;
       	LongBinaryOperator opFunc = selectOperator(op);
            if(opFunc != null && !this.isTop(type) && !interval.isTop(type)) {
                long resultBoundLowerBounds = opFunc.applyAsLong(this.lowerBound, interval.lowerBound);
                long resultBoundUpperBounds = opFunc.applyAsLong(this.upperBound, interval.upperBound);
                // The lower and upper bounds may switch depending on the operation (like MUL).
                long newLowerBound = Math.min(resultBoundLowerBounds,resultBoundUpperBounds);
                long newUpperBound = Math.max(resultBoundLowerBounds,resultBoundUpperBounds);

                newInterval = new Interval(newLowerBound,newUpperBound);

            } else {
                newInterval = Interval.getTop(type);
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

package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.integers.IntBinaryOp;
import com.dat3m.dartagnan.expression.integers.IntCmpOp;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.program.Register;
import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.function.BiFunction;

public class Interval {
    public BigInteger lowerBound;
    public BigInteger upperBound;

    public Interval(BigInteger lowerBound, BigInteger upperBound) {
        Preconditions.checkArgument(lowerBound.compareTo(upperBound) <= 0);
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
            return new Interval(itype.getMinimumValue(true),itype.getMaximumValue(false));
        } else
        if(type instanceof BooleanType) {
            return new Interval(BigInteger.ZERO,BigInteger.ONE);
        }
        else throw new RuntimeException("Unsupported type: " + type.getClass());

    }


    // FIXME: better name needed
    public static Interval makeDefault(BigInteger value) {
        return new Interval(value,value);
    }

    public Interval join(Interval interval2) {
        return new Interval(this.lowerBound.min(interval2.lowerBound),this.upperBound.max(interval2.upperBound));    }

    public boolean isTop(Type type) {
	    return this.equals(Interval.getTop(type));
    }

//    public Interval add(Interval interval2) {
//        final long newLowerBound = this.lowerBound+interval2.lowerBound;
//        final long newUpperBound = this.upperBound+interval2.upperBound;
//        return new Interval(newLowerBound,newUpperBound);
//    }


    public BiFunction<BigInteger,BigInteger,BigInteger> selectOperator(IntBinaryOp op) {
        return switch (op) {
            case ADD -> BigInteger::add;
            case SUB -> BigInteger::subtract;
            case MUL -> BigInteger::multiply;
	        case OR -> BigInteger::or;
	        case AND -> BigInteger::and;
	        default -> null;
        };
    }
    public Interval applyOperator(IntBinaryOp op, Interval interval,Type type) {
	    Interval newInterval;
       	BiFunction<BigInteger,BigInteger,BigInteger> opFunc = selectOperator(op);
            if(opFunc != null && !this.isTop(type) && !interval.isTop(type)) {
                BigInteger resultBoundLowerBounds = opFunc.apply(this.lowerBound, interval.lowerBound);
                BigInteger resultBoundUpperBounds = opFunc.apply(this.upperBound, interval.upperBound);
                // The lower and upper bounds may switch depending on the operation (like MUL).
                BigInteger newLowerBound = resultBoundLowerBounds.min(resultBoundUpperBounds);
                BigInteger newUpperBound = resultBoundLowerBounds.max(resultBoundUpperBounds);

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
	    return (otherInterval.lowerBound.equals(this.lowerBound) && otherInterval.upperBound.equals(this.upperBound));
    }

    @Override
    public String toString() {
        return "[ " + this.lowerBound + ", " + this.upperBound + " ]";
    }
}

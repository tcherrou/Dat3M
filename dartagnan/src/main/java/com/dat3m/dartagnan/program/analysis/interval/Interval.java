package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.integers.IntBinaryOp;
import com.dat3m.dartagnan.expression.integers.IntCmpOp;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.program.Register;
import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

public class Interval {
    public BigInteger lowerBound;
    public BigInteger upperBound;

    public Interval(BigInteger lowerBound, BigInteger upperBound) {
        Preconditions.checkArgument(lowerBound.compareTo(upperBound) <= 0);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    Logger logger = LogManager.getLogger(Interval.class);

    static Set<Object> unsupportedOperators = new HashSet<>();

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
    public BigInteger size() {
        return (this.upperBound.subtract(this.lowerBound)).add(BigInteger.ONE);
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


    private static BigInteger getBiggerbitLength(BigInteger x, BigInteger y) {
        int lengthx = (x.signum() > 0) ? x.bitLength() : x.bitLength() + 1;
        int lengthy = (y.signum() > 0) ? y.bitLength() : y.bitLength() + 1;

        return (lengthx >= lengthy) ? x : y;
    }
    // Algorithms based on
    // https://doc.lagout.org/security/Hackers%20Delight.pdf#page=75&zoom=100,6,565
    public static BigInteger minOR(BigInteger lb1,BigInteger lb2,BigInteger ub1,BigInteger ub2) {
        BigInteger largestBitLength = getBiggerbitLength(lb1,lb2);

        BigInteger m = BigInteger.TWO.pow(largestBitLength.bitLength());
        BigInteger temp;
        while (!m.equals(BigInteger.ZERO)) {
            if(!(lb1.not().and(lb2).and(m).equals(BigInteger.ZERO))) {
               temp = (lb1.or(m).and(m.negate()));
               if(temp.compareTo(ub1) <= 0) { lb1 = temp;break; }
            } else if (!(lb1.and(lb2.not()).and(m).equals(BigInteger.ZERO))) {
                temp = (lb2.or(m).and(m.negate()));
                if(temp.compareTo(ub2) <= 0) { lb2 = temp;break; }
            }
            m = m.shiftRight(1);
        }
        return lb1.or(lb2);

    }


    public static BigInteger maxOR(BigInteger lb1,BigInteger lb2,BigInteger ub1,BigInteger ub2) {
        BigInteger largestBitLength = getBiggerbitLength(ub1,ub2);
        BigInteger m = BigInteger.TWO.pow(largestBitLength.bitLength());
        BigInteger temp;
        while (!m.equals(BigInteger.ZERO)) {
            if(!ub1.and(ub2).and(m).equals(BigInteger.ZERO)) {
                temp = (ub1.subtract(m)).or(m.subtract(BigInteger.ONE));
                if (temp.compareTo(lb1) >= 0) { ub1 = temp;break; }
                temp = (ub2.subtract(m)).or(m.subtract(BigInteger.ONE));
                if (temp.compareTo(lb2) >= 0) { ub2 = temp;break; }
            }
            m = m.shiftRight(1);
        }
        return ub1.or(ub2);
    }

    private char constructSignNumber(BigInteger lb1, BigInteger lb2, BigInteger ub1, BigInteger ub2) {
        char signs = 0b0000;
        if(lb1.signum() > 0) signs |= 0b1000;
        if(lb2.signum() > 0) signs |= 0b0100;
        if(ub1.signum() > 0) signs |= 0b0010;
        if(ub2.signum() > 0) signs |= 0b0001;

        return signs;
    }

    private BigInteger setAllBits(int length) {
        char[] ones = new char[length];
        Arrays.fill(ones,'1');
        return new BigInteger(new String(ones));
    }

    private class BigIntPair {
        BigInteger min,max;

        BigIntPair() {}


    }
    private BigIntPair doOR(BigInteger lb1, BigInteger lb2, BigInteger ub1, BigInteger ub2) {
        char signs = constructSignNumber(lb1, ub1, lb2, ub2);
       // logger.warn("{}, {}, {}, {}",lb1,ub1,lb2,ub2);
        BigIntPair pair = new BigIntPair();
       // logger.warn(Integer.toBinaryString(signs));
        switch (signs) {
            case 0b1111:
            case 0b0000:
            case 0b0011:
            case 0b1100: {pair.min = minOR(lb1,lb2,ub1,ub2); pair.max = maxOR(lb1,lb2,ub1,ub2); break;}
            case 0b0001: { pair.min = lb1; pair.max = new BigInteger("-1"); break;}
            case 0b0100: {pair.min = lb2; pair.max = new BigInteger("-1"); break;}
            case 0b0101: {pair.min = lb1.min(lb2); pair.max = maxOR(BigInteger.ZERO,BigInteger.ZERO,ub1,ub2); break;}
            case 0b0111: {pair.min = minOR(lb1,lb2,setAllBits(ub1.bitLength()),ub2);
                pair.max = maxOR(BigInteger.ZERO,lb2,ub1,ub2);
                break;
            }
            case 0b1101: { pair.min = minOR(lb1,lb2,ub1,setAllBits(ub2.bitLength()));
                pair.max = maxOR(lb1,BigInteger.ZERO,ub1,ub2);
                break;
            }
            default: return null;

        }
        return pair;

    }


    private BigInteger minAND(BigInteger lb1,BigInteger lb2, BigInteger ub1,BigInteger ub2) {
        BigIntPair pair = doOR(ub1.not(),ub2.not(),lb1.not(),lb2.not());
        assert pair != null;
        return pair.max.not();
    }

    private BigInteger maxAND(BigInteger lb1,BigInteger lb2, BigInteger ub1,BigInteger ub2) {
        BigIntPair pair = doOR(ub1.not(),ub2.not(),lb1.not(),lb2.not());
        assert pair != null;
        return pair.min.not();
    }


    private BigIntPair doAND(BigInteger lb1,BigInteger lb2, BigInteger ub1,BigInteger ub2) {
        BigIntPair pair = new BigIntPair();
        pair.min = minAND(lb1,lb2,ub1,ub2);
        pair.max = maxAND(lb1,ub2,ub1,ub2);
        return pair;
    }

//    private BigIntPair add(BigInteger lb1, BigInteger ub1,BigInteger lb2,BigInteger ub2, Type type) {
//        BigIntPair bounds = new BigIntPair();
//        if (type instanceof IntegerType itype) {
//            BigInteger newLowerBound = lb1.add(lb2);
//            BigInteger newUpperBound = ub1.add(ub2);
//            if(newUpperBound.compareTo(itype.getMaximumValue(false)) > 0 &&
//                    newLowerBound.compareTo(itype.getMaximumValue(false)) < 0) {
//                bounds.min()
//            }
//        }
//    }


    public BiFunction<BigInteger,BigInteger,BigInteger> selectBinaryOperator(IntBinaryOp op) {
        return switch (op) {
            // Not considering overflows
            case ADD -> BigInteger::add;
            case SUB -> BigInteger::subtract;
            case MUL -> BigInteger::multiply;
	        //case OR ->  {((x,y) -> {return x;}) ;
            //}
	        //case AND -> BigInteger::and;
	        default -> {
                unsupportedOperators.add(op);
                yield null;
            }
        };
    }

    @FunctionalInterface
    interface LogicalOperation<P1,P2,P3,P4,O> {
        O apply(P1 p1, P2 p2, P3 p3,P4 p4);
    }

    private LogicalOperation<BigInteger,
            BigInteger,
            BigInteger,
            BigInteger,
            BigIntPair> selectLogicalOperator(IntBinaryOp op) {
        return switch (op) {
            case OR -> this::doOR;
            case AND -> this::doAND;
            default -> null;
        };
    }


    public BiFunction<BigInteger,Integer,BigInteger> selectShiftOperator(IntBinaryOp op) {
        return switch (op) {
            case RSHIFT -> BigInteger::shiftRight;
            case LSHIFT -> BigInteger::shiftLeft;
            default -> null;
        };
    }


    public Interval applyLogicOperator(IntBinaryOp op, Interval interval, Type type) {
        Interval newInterval;
        LogicalOperation<BigInteger,BigInteger,BigInteger,BigInteger,BigIntPair> opFunc = selectLogicalOperator(op);
        if (opFunc != null && !this.isTop(type) && !interval.isTop(type)) {
            BigIntPair newBounds = opFunc.apply(this.lowerBound,interval.lowerBound,this.upperBound,interval.upperBound);
            newInterval = new Interval(newBounds.min,newBounds.max);
        } else newInterval = Interval.getTop(type);
        return newInterval;
    }




    public Interval applyArithmeticOperator(IntBinaryOp op, Interval interval,Type type) {
	    Interval newInterval;
       	BiFunction<BigInteger,BigInteger,BigInteger> opFunc = selectBinaryOperator(op);
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


    public Interval applyOperator(IntBinaryOp op, Interval interval, Type type) {
        return switch (op) {
            case ADD, SUB, MUL -> applyArithmeticOperator(op,interval,type);
            case OR, AND -> applyLogicOperator(op,interval,type);
            default -> {
                unsupportedOperators.add(op);
                yield Interval.getTop(type);
            }
        };
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

    public static Set<Object> getUnsupportedOperatorsFound() {
        return unsupportedOperators;
    }

    @Override
    public String toString() {
        return "[ " + this.lowerBound + ", " + this.upperBound + " ]";
    }
}

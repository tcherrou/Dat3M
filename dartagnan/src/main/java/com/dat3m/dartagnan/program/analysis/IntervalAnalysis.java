package com.dat3m.dartagnan.program.analysis;

public class IntervalAnalysis {

    static class Interval {
        public int lowerBound;
        public int upperBound;
        Interval(int lowerBound, int upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }
    }
}

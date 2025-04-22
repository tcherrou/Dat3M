package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import org.sosy_lab.common.configuration.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NaiveIntervalAnalysis implements IntervalAnalysis{

    static Logger logger = LogManager.getLogger(NaiveIntervalAnalysis.class);

    static NaiveIntervalAnalysis fromConfig(Program program, Context analysisContext, VerificationTask task, Configuration config) {
        return new NaiveIntervalAnalysis();
    }

    NaiveIntervalAnalysis() { }

    @Override
    public Interval getIntervalAt(Event event, Register r) {
        return Interval.getTop(r.getType());
    }
}

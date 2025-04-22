package com.dat3m.dartagnan.program.analysis.interval;

import com.dat3m.dartagnan.configuration.IntervalOptions;
import com.dat3m.dartagnan.verification.VerificationTask;

import static com.dat3m.dartagnan.configuration.OptionNames.INTERVAL_METHOD;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.dat3m.dartagnan.utils.Utils;


import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;


public interface IntervalAnalysis {

    Logger logger = LogManager.getLogger(IntervalAnalysis.class);

    @Options
    class Config {

        @Option(
                name = INTERVAL_METHOD,
                description = "Indicates how to compute itervals for registers.")
                private IntervalOptions method = IntervalOptions.getDefault();

        Config(Configuration config) throws InvalidConfigurationException {
            config.inject(this);
        }
    }


    public static IntervalAnalysis fromConfig(Program program, Context analysisContext, VerificationTask task, Configuration config) throws InvalidConfigurationException {
        Config c = new Config(config);
        logger.info("Selected interval analysis: {}", c.method);
        long t0 = System.currentTimeMillis();
        IntervalAnalysis analysis = switch (c.method) {
            case NAIVE -> NaiveIntervalAnalysis.fromConfig(program,analysisContext,task,config);
            case PATTERSON -> IntervalAnalysisPatterson.fromConfig(program,analysisContext,task,config);
        };
        long t1 = System.currentTimeMillis();
        logger.info("Finished interval analysis in {}", Utils.toTimeString(t1 - t0));
        return analysis;
    }



    public Interval getIntervalAt(Event event,Register r);
}

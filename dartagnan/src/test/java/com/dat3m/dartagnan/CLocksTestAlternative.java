package com.dat3m.dartagnan;

import com.dat3m.dartagnan.analysis.Refinement;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.utils.rules.CSVLogger;
import com.dat3m.dartagnan.utils.rules.Provider;
import com.dat3m.dartagnan.utils.rules.Providers;
import com.dat3m.dartagnan.utils.rules.RequestShutdownOnError;
import com.dat3m.dartagnan.verification.RefinementTask;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;

import java.io.IOException;
import java.util.Arrays;

import static com.dat3m.dartagnan.analysis.Base.runAnalysisAssumeSolver;
import static com.dat3m.dartagnan.utils.ResourceHelper.TEST_RESOURCE_PATH;
import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.UNKNOWN;
import static com.dat3m.dartagnan.wmm.utils.Arch.*;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CLocksTestAlternative {

    private String name;
    private Arch target;
    private final Result expected;

    @ClassRule
    public static CSVLogger.Initialization csvInit = CSVLogger.Initialization.create();

    // Provider rules
    private final Provider<ShutdownManager> shutdownManagerProvider = Provider.fromSupplier(ShutdownManager::create);
    private final Provider<Arch> targetProvider = () -> target;
    private final Provider<String> filePathProvider = Provider.fromSupplier(() -> TEST_RESOURCE_PATH + "locks/" + name + ".bpl");
    private final Provider<Settings> settingsProvider = Provider.fromSupplier(() -> new Settings(Alias.CFIS, 1, 0));
    private final Provider<Program> programProvider = Providers.createProgramFromPath(filePathProvider);
    private final Provider<Wmm> wmmProvider = Providers.createWmmFromArch(targetProvider);
    private final Provider<VerificationTask> taskProvider = Providers.createTask(programProvider, wmmProvider, targetProvider, settingsProvider);
    private final Provider<SolverContext> contextProvider = Providers.createSolverContextFromManager(shutdownManagerProvider);
    private final Provider<ProverEnvironment> proverProvider = Providers.createProverWithFixedOptions(contextProvider, ProverOptions.GENERATE_MODELS);

    // Special rules
    private final Timeout timeout = Timeout.millis(60000);
    private final CSVLogger csvLogger = CSVLogger.create(() -> String.format("%s-%s", name, target));
    private final RequestShutdownOnError shutdownOnError = RequestShutdownOnError.create(shutdownManagerProvider);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(filePathProvider)
            .around(shutdownManagerProvider)
            .around(shutdownOnError)
            .around(settingsProvider)
            .around(programProvider)
            .around(wmmProvider)
            .around(taskProvider)
            .around(csvLogger)
            .around(timeout)
            // Context/Prover need to be created inside test-thread spawned by <timeout>
            .around(contextProvider)
            .around(proverProvider);

    public CLocksTestAlternative(String name, Arch target, Result expected) {
        this.name = name;
        this.target = target;
        this.expected = expected;
    }

	@Parameterized.Parameters(name = "{index}: {0}, target={1}")
    public static Iterable<Object[]> data() throws IOException {
		return Arrays.asList(new Object[][]{
	            {"ttas-5", TSO, UNKNOWN},
	            {"ttas-5", ARM8, UNKNOWN},
	            {"ttas-5", POWER, UNKNOWN},
	            {"ttas-5-acq2rx", TSO, UNKNOWN},
	            {"ttas-5-acq2rx", ARM8, UNKNOWN},
	            {"ttas-5-acq2rx", POWER, UNKNOWN},
	            {"ttas-5-rel2rx", TSO, UNKNOWN},
	            {"ttas-5-rel2rx", ARM8, FAIL},
	            {"ttas-5-rel2rx", POWER, FAIL},
	            {"ticketlock-3", TSO, UNKNOWN},
	            {"ticketlock-3", ARM8, UNKNOWN},
	            {"ticketlock-3", POWER, UNKNOWN},
	            {"ticketlock-3-acq2rx", TSO, UNKNOWN},
	            {"ticketlock-3-acq2rx", ARM8, UNKNOWN},
	            {"ticketlock-3-acq2rx", POWER, UNKNOWN},
	            {"ticketlock-3-rel2rx", TSO, UNKNOWN},
	            {"ticketlock-3-rel2rx", ARM8, FAIL},
	            {"ticketlock-3-rel2rx", POWER, FAIL},
                {"mutex-3", TSO, UNKNOWN},
                {"mutex-3", ARM8, UNKNOWN},
                {"mutex-3", POWER, UNKNOWN},
                {"mutex-3-acq2rx-futex", TSO, UNKNOWN},
                {"mutex-3-acq2rx-futex", ARM8, UNKNOWN},
                {"mutex-3-acq2rx-futex", POWER, UNKNOWN},
                {"mutex-3-acq2rx-lock", TSO, UNKNOWN},
                {"mutex-3-acq2rx-lock", ARM8, UNKNOWN},
                {"mutex-3-acq2rx-lock", POWER, UNKNOWN},
                {"mutex-3-rel2rx-futex", TSO, UNKNOWN},
                {"mutex-3-rel2rx-futex", ARM8, UNKNOWN},
                {"mutex-3-rel2rx-futex", POWER, UNKNOWN},
                {"mutex-3-rel2rx-unlock", TSO, UNKNOWN},
                {"mutex-3-rel2rx-unlock", ARM8, FAIL},
                {"mutex-3-rel2rx-unlock", POWER, FAIL},
                {"spinlock-5", TSO, UNKNOWN},
                {"spinlock-5", ARM8, UNKNOWN},
                {"spinlock-5", POWER, UNKNOWN},
                {"spinlock-5-acq2rx", TSO, UNKNOWN},
                {"spinlock-5-acq2rx", ARM8, UNKNOWN},
                {"spinlock-5-acq2rx", POWER, UNKNOWN},
                {"spinlock-5-rel2rx", TSO, UNKNOWN},
                {"spinlock-5-rel2rx", ARM8, FAIL},
                {"spinlock-5-rel2rx", POWER, FAIL},
                {"linuxrwlock-3", TSO, UNKNOWN},
                {"linuxrwlock-3", ARM8, UNKNOWN},
                {"linuxrwlock-3", POWER, UNKNOWN},
                {"linuxrwlock-3-acq2rx", TSO, UNKNOWN},
                {"linuxrwlock-3-acq2rx", ARM8, FAIL},
                {"linuxrwlock-3-acq2rx", POWER, FAIL},
                {"linuxrwlock-3-rel2rx", TSO, UNKNOWN},
                {"linuxrwlock-3-rel2rx", ARM8, FAIL},
                {"linuxrwlock-3-rel2rx", POWER, FAIL},
                {"mutex_musl-3", TSO, UNKNOWN},
                {"mutex_musl-3", ARM8, UNKNOWN},
                {"mutex_musl-3", POWER, UNKNOWN},
                {"mutex_musl-3-acq2rx-futex", TSO, UNKNOWN},
                {"mutex_musl-3-acq2rx-futex", ARM8, UNKNOWN},
                {"mutex_musl-3-acq2rx-futex", POWER, UNKNOWN},
                {"mutex_musl-3-acq2rx-lock", TSO, UNKNOWN},
                {"mutex_musl-3-acq2rx-lock", ARM8, UNKNOWN},
                {"mutex_musl-3-acq2rx-lock", POWER, UNKNOWN},
                {"mutex_musl-3-rel2rx-futex", TSO, UNKNOWN},
                {"mutex_musl-3-rel2rx-futex", ARM8, UNKNOWN},
                {"mutex_musl-3-rel2rx-futex", POWER, UNKNOWN},
                {"mutex_musl-3-rel2rx-unlock", TSO, UNKNOWN},
                {"mutex_musl-3-rel2rx-unlock", ARM8, FAIL},
                {"mutex_musl-3-rel2rx-unlock", POWER, FAIL}
        });

    }

    @Test
    @CSVLogger.FileName("csv/assume")
    public void testAssume() throws Exception {
	    assertEquals(expected, runAnalysisAssumeSolver(contextProvider.get(), proverProvider.get(), taskProvider.get()));
    }

    @Test
    @CSVLogger.FileName("csv/refinement")
    public void testRefinement() throws Exception {
            assertEquals(expected, Refinement.runAnalysisSaturationSolver(contextProvider.get(), proverProvider.get(),
                    RefinementTask.fromVerificationTaskWithDefaultBaselineWMM(taskProvider.get())));
    }
}
package os;

import org.junit.jupiter.api.Test;

/** Runs the dependency-free regression checks as part of the Maven test lifecycle. */
class OperatingSystemsIntegrationTest {
    @Test
    void allModulesPassRegressionChecks() throws Exception {
        RegressionSuite.main(new String[0]);
    }
}

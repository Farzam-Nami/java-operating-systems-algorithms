package os.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

/** Verifies the command-line interface and its error handling. */
class AppTest {

    @Test
    void defaultsRunTheOriginalExercise() {
        CliOutput result = execute();
        assertEquals(0, result.code);
        assertTrue(result.stdout.contains("Requests: 10 | Hits: 4 | Faults: 6 | Fault rate: 60.00%"));
        assertTrue(result.stdout.contains("[2, 6, 7, 5]"));
        assertTrue(result.stderr.isEmpty());
    }

    @Test
    void customFrameCountAndPagesAreSupported() {
        CliOutput result = execute("--frames", "3", "--pages", "1,2,3,2,4");
        assertEquals(0, result.code);
        assertTrue(result.stdout.contains("Frames: 3"));
        assertTrue(result.stdout.contains("Requests: 5 | Hits: 1 | Faults: 4"));
    }

    @Test
    void pageListMayContainWhitespaceAndRepeatedValues() {
        CliOutput result = execute("--pages", " 9, 9, 3 ");
        assertEquals(0, result.code);
        assertTrue(result.stdout.contains("Requests: 3 | Hits: 1 | Faults: 2"));
    }

    @Test
    void optionsWorkInEitherOrder() {
        CliOutput result = execute("--pages", "1,2", "--frames", "1");
        assertEquals(0, result.code);
        assertTrue(result.stdout.contains("Frames: 1"));
        assertTrue(result.stdout.contains("Faults: 2"));
    }

    @Test
    void emptyPageSequenceIsSupported() {
        CliOutput result = execute("--pages", "");
        assertEquals(0, result.code);
        assertTrue(result.stdout.contains("Requests: 0 | Hits: 0 | Faults: 0 | Fault rate: 0.00%"));
    }

    @Test
    void helpHasNoSimulationOutput() {
        CliOutput result = execute("--help");
        assertEquals(0, result.code);
        assertTrue(result.stdout.contains("Usage:"));
        assertFalse(result.stdout.contains("Step | Page"));
    }

    @Test
    void malformedAndInvalidInputsReturnErrorStatus() {
        String[][] badArgs = {
            {"--frames", "0"}, {"--frames", "-1"}, {"--frames", "abc"},
            {"--frames"}, {"--pages"}, {"--pages", "2,,3"},
            {"--pages", "1,-4"}, {"--pages", "1,2,"},
            {"--pages", "99999999999999"}, {"--unknown"},
            {"--frames", "2", "--frames", "3"},
            {"--pages", "1", "--pages", "2"},
            {"--help", "--frames", "2"}
        };
        for (String[] arguments : badArgs) {
            CliOutput result = execute(arguments);
            assertEquals(2, result.code);
            assertTrue(result.stderr.contains("Error:"));
            assertTrue(result.stdout.isEmpty());
        }
    }

    /** Captures console output without starting a new Java process. */
    private static CliOutput execute(String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = App.run(arguments, new PrintStream(stdout), new PrintStream(stderr));
        return new CliOutput(code, stdout.toString(), stderr.toString());
    }

    /** Immutable collection of command-line exit status and captured streams. */
    private static final class CliOutput {
        private final int code;
        private final String stdout;
        private final String stderr;

        private CliOutput(int code, String stdout, String stderr) {
            this.code = code;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}

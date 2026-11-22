package os.paging;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Command-line entry point for the LRU page replacement demonstration. */
public final class App {
    private static final int DEFAULT_FRAMES = 4;
    private static final List<Integer> DEFAULT_PAGES = Arrays.asList(2, 2, 1, 4, 5, 2, 5, 6, 5, 7);

    private App() {
        // Utility class: instances are unnecessary.
    }

    /** Runs the simulator and exits with a nonzero status for invalid arguments. */
    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Parses optional arguments, executes one simulation, and writes its trace.
     *
     * @param args optional --frames N, --pages A,B,C, or --help arguments
     * @param out destination for simulation output and help text
     * @param err destination for invalid-argument messages
     * @return 0 for success, 2 for invalid input
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        int frames = DEFAULT_FRAMES;
        List<Integer> pages = DEFAULT_PAGES;
        boolean framesProvided = false;
        boolean pagesProvided = false;
        try {
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--help".equals(argument)) {
                    if (args.length != 1) {
                        throw new IllegalArgumentException("--help must be used on its own.");
                    }
                    printUsage(out);
                    return 0;
                }
                if ("--frames".equals(argument)) {
                    if (framesProvided) {
                        throw new IllegalArgumentException("--frames cannot be repeated.");
                    }
                    if (++index >= args.length) {
                        throw new IllegalArgumentException("Missing value for --frames.");
                    }
                    frames = Integer.parseInt(args[index]);
                    framesProvided = true;
                } else if ("--pages".equals(argument)) {
                    if (pagesProvided) {
                        throw new IllegalArgumentException("--pages cannot be repeated.");
                    }
                    if (++index >= args.length) {
                        throw new IllegalArgumentException("Missing value for --pages.");
                    }
                    pages = parsePages(args[index]);
                    pagesProvided = true;
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }
            SimulationResult report = new LruPageReplacement(frames).simulate(pages);
            printReport(report, out);
            return 0;
        } catch (IllegalArgumentException exception) {
            err.println("Error: " + exception.getMessage());
            err.println("Run with --help to see valid arguments.");
            return 2;
        }
    }

    /** Parses a comma-separated sequence of non-negative page IDs. */
    private static List<Integer> parsePages(String input) {
        if (input.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] tokens = input.split(",", -1);
        List<Integer> pages = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Page IDs cannot be empty.");
            }
            int page = Integer.parseInt(value);
            if (page < 0) {
                throw new IllegalArgumentException("Page IDs must be non-negative.");
            }
            pages.add(page);
        }
        return pages;
    }

    /** Formats the full reference trace and summary statistics. */
    private static void printReport(SimulationResult report, PrintStream out) {
        out.println("LRU Page Replacement Simulation");
        out.println("Frames: " + report.getFrameCount());
        out.println("Step | Page | Outcome       | Frame | Evicted | Frames");
        out.println("-----+------+---------------+-------+---------+---------------------");
        for (PageAccess access : report.getAccesses()) {
            String evicted = access.getEvictedPage() == null ? "-" : access.getEvictedPage().toString();
            out.printf(Locale.ROOT, "%4d | %4d | %-13s | %5d | %7s | %s%n",
                    access.getRequestIndex() + 1, access.getPageId(), access.getOutcome(),
                    access.getFrameIndex(), evicted, formatFrames(access.getFrames()));
        }
        out.printf(Locale.ROOT, "Requests: %d | Hits: %d | Faults: %d | Fault rate: %.2f%%%n",
                report.getRequestCount(), report.getHitCount(), report.getFaultCount(),
                report.getFaultRate() * 100.0);
    }

    /** Represents empty frame slots with a dash for readability. */
    private static String formatFrames(List<Integer> frames) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(frames.get(i) == null ? "-" : frames.get(i));
        }
        return builder.append(']').toString();
    }

    /** Displays supported command-line options and an example invocation. */
    private static void printUsage(PrintStream out) {
        out.println("Usage: java -cp target/classes os.paging.App [--frames N] [--pages A,B,C]");
        out.println("  --frames N       Positive number of frames (default: 4)");
        out.println("  --pages A,B,C    Comma-separated non-negative page IDs (default: original exercise)");
        out.println("  --pages \"\"     Simulate an empty reference sequence");
        out.println("  --help           Show this help");
        out.println("Example: java -cp target/classes os.paging.App --frames 3 --pages 1,2,3,2,4");
    }
}

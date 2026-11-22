package os;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import os.memory.FitStrategy;
import os.memory.FixedPartitionSimulator;
import os.memory.PartitionAllocator;
import os.memory.VariablePartitionSimulator;
import os.paging.FifoPageReplacement;
import os.paging.LruPageReplacement;
import os.paging.PageAccess;
import os.paging.SimulationResult;
import os.scheduling.PriorityScheduler;
import os.scheduling.ProcessSpec;
import os.synchronization.DiningPhilosophersSimulator;
import os.synchronization.ProducerConsumerDemo;

/** Command-line demonstrations for the operating-systems coursework modules. */
public final class App {
    private static final List<Integer> PAGES = Arrays.asList(2, 2, 1, 4, 5, 2, 5, 6, 5, 7);

    private App() { }

    /** Executes a module demo; use the help command to list modules. */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Parses the subcommand and reports success (0) or invalid usage (2).
     *
     * @param args command-line arguments
     * @param out normal output stream
     * @param err error output stream
     * @return process exit code
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || out == null || err == null) {
            throw new IllegalArgumentException("Arguments and streams must not be null.");
        }
        String command = args.length == 0 ? "all" : args[0];
        try {
            if ("help".equals(command) || "--help".equals(command)) {
                requireArgs(args, 1);
                usage(out);
            } else if ("all".equals(command)) {
                requireArgs(args, args.length == 0 ? 0 : 1);
                runPaging(false, 4, PAGES, out);
                runPaging(true, 4, PAGES, out);
                runAllocation(out);
                runMft(out);
                runMvt(out);
                runScheduling(out);
                runProducerConsumer(out);
                runDining(out);
            } else if ("lru".equals(command) || "fifo".equals(command)) {
                if (args.length != 1 && args.length != 3) {
                    throw new IllegalArgumentException("Paging usage: " + command + " [frame-count comma-separated-pages]");
                }
                int frames = args.length == 3 ? Integer.parseInt(args[1]) : 4;
                List<Integer> pages = args.length == 3 ? parsePages(args[2]) : PAGES;
                runPaging("fifo".equals(command), frames, pages, out);
            } else {
                requireArgs(args, 1);
                switch (command) {
                    case "allocation": runAllocation(out); break;
                    case "mft": runMft(out); break;
                    case "mvt": runMvt(out); break;
                    case "scheduling": runScheduling(out); break;
                    case "producer-consumer": runProducerConsumer(out); break;
                    case "dining-philosophers": runDining(out); break;
                    default: throw new IllegalArgumentException("Unknown module: " + command);
                }
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println("Invalid input: " + ex.getMessage());
            err.println("Use 'help' for supported commands.");
            return 2;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            err.println("Simulation interrupted.");
            return 1;
        } catch (IllegalStateException ex) {
            err.println("Simulation failed: " + ex.getMessage());
            return 1;
        }
    }

    /** Rejects unexpected arguments for a single-command demonstration. */
    private static void requireArgs(String[] args, int count) {
        if (args.length != count) {
            throw new IllegalArgumentException("Unexpected arguments.");
        }
    }

    /** Parses page IDs; an empty string represents an empty reference sequence. */
    private static List<Integer> parsePages(String input) {
        List<Integer> result = new ArrayList<>();
        if (input.isEmpty()) { return result; }
        for (String raw : input.split(",", -1)) {
            String token = raw.trim();
            if (token.isEmpty()) { throw new IllegalArgumentException("Page IDs cannot be blank."); }
            result.add(Integer.parseInt(token));
        }
        return result;
    }

    /** Prints per-reference outcomes and aggregate statistics. */
    private static void runPaging(boolean fifo, int frames, List<Integer> pages, PrintStream out) {
        SimulationResult report = fifo ? new FifoPageReplacement(frames).simulate(pages)
                : new LruPageReplacement(frames).simulate(pages);
        out.println("\n=== " + (fifo ? "FIFO" : "LRU") + " Page Replacement ===");
        for (PageAccess access : report.getAccesses()) {
            out.printf(Locale.ROOT, "Page %d: %s, frames=%s, evicted=%s%n",
                    access.getPageId(), access.getOutcome(), access.getFrames(),
                    access.getEvictedPage() == null ? "-" : access.getEvictedPage());
        }
        out.printf(Locale.ROOT, "Hits=%d, Faults=%d, Fault rate=%.2f%%%n",
                report.getHitCount(), report.getFaultCount(), report.getFaultRate() * 100);
    }

    /** Compares first-fit, best-fit and worst-fit using identical initial partitions. */
    private static void runAllocation(PrintStream out) {
        out.println("\n=== Fixed-Partition Fit Policies ===");
        int[] partitions = {100, 50, 200, 400, 150};
        int[] processes = {70, 110, 45, 400, 600, 140};
        for (FitStrategy strategy : FitStrategy.values()) {
            PartitionAllocator allocator = new PartitionAllocator(partitions, strategy);
            List<Integer> assignments = new ArrayList<>();
            for (int process : processes) {
                assignments.add(allocator.allocate(process));
            }
            out.printf(Locale.ROOT, "%s: assignments=%s, internal fragmentation=%d bytes%n",
                    strategy, assignments, allocator.getInternalFragmentationBytes());
        }
    }

    /** Shows fixed-block allocation decisions and accounting. */
    private static void runMft(PrintStream out) {
        out.println("\n=== MFT ===");
        FixedPartitionSimulator.Result report = new FixedPartitionSimulator()
                .simulate(1024, 256, Arrays.asList(120, 300, 200, 64, 80));
        out.println("Accepted=" + report.getAllocationDecisions()
                + ", blocks=" + report.getPartitionCount()
                + ", internal fragmentation=" + report.getInternalFragmentationBytes());
    }

    /** Shows sequential variable-size allocation without deallocation. */
    private static void runMvt(PrintStream out) {
        out.println("\n=== MVT ===");
        VariablePartitionSimulator.Result report = new VariablePartitionSimulator()
                .simulate(500, Arrays.asList(140, 220, 200, 100));
        out.println("Accepted=" + report.getAllocationDecisions()
                + ", allocated=" + report.getAllocatedBytes()
                + ", contiguous free tail=" + report.getFreeBytes());
    }

    /** Displays non-preemptive priority execution order and average times. */
    private static void runScheduling(PrintStream out) {
        out.println("\n=== Priority Scheduling ===");
        PriorityScheduler.Result report = new PriorityScheduler().schedule(Arrays.asList(
                new ProcessSpec("P1", 5, 1), new ProcessSpec("P2", 3, 3),
                new ProcessSpec("P3", 2, 2), new ProcessSpec("P4", 4, 3)));
        for (PriorityScheduler.Entry entry : report.getEntries()) {
            out.printf(Locale.ROOT, "%s: wait=%d, turnaround=%d%n",
                    entry.getProcess().getId(), entry.getWaitingTime(), entry.getTurnaroundTime());
        }
        out.printf(Locale.ROOT, "Mean wait=%.2f, mean turnaround=%.2f%n",
                report.getAverageWaitingTime(), report.getAverageTurnaroundTime());
    }

    /** Runs a bounded buffer with a finite sequence of tokens. */
    private static void runProducerConsumer(PrintStream out) throws InterruptedException {
        out.println("\n=== Producer / Consumer (Semaphores) ===");
        out.println("Consumed=" + ProducerConsumerDemo.run(Arrays.asList("a", "b", "c", "d", "e"), 3));
    }

    /** Runs a finite deadlock-free dining-philosophers demonstration. */
    private static void runDining(PrintStream out) throws InterruptedException {
        out.println("\n=== Dining Philosophers ===");
        out.println("Meals per philosopher="
                + Arrays.toString(new DiningPhilosophersSimulator().simulate(5, 2)));
    }

    /** Lists available runnable coursework modules. */
    private static void usage(PrintStream out) {
        out.println("Usage: java -jar operating-systems-algorithms-1.0.0.jar [command]");
        out.println("Commands: all, lru, fifo, allocation, mft, mvt, scheduling,");
        out.println("          producer-consumer, dining-philosophers, help");
        out.println("Paging examples: lru 3 1,2,3,2,4  |  fifo 3 1,2,3,2,4");
        out.println("Each non-paging command runs a documented sample exercise.");
    }
}

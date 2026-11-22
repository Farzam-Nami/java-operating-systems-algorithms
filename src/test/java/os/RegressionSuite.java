package os;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import os.synchronization.BoundedBuffer;
import os.synchronization.DiningPhilosophersSimulator;
import os.synchronization.ProducerConsumerDemo;

/**
 * Self-contained regression suite runnable with only a Java 11 JDK.
 * Its randomized cases use independent reference models and fixed seeds.
 */
public final class RegressionSuite {
    private static int checks;
    private static int groups;

    private RegressionSuite() { }

    /** Runs all checks and exits with a nonzero status if any assertion fails. */
    public static void main(String[] args) throws Exception {
        execute("LRU original exercise", RegressionSuite::lruOriginal);
        execute("LRU randomized oracle", RegressionSuite::lruRandomized);
        execute("FIFO known traces", RegressionSuite::fifoOriginal);
        execute("FIFO randomized oracle", RegressionSuite::fifoRandomized);
        execute("Page validation and immutability", RegressionSuite::pageValidation);
        execute("Partition fit policies", RegressionSuite::fitKnown);
        execute("Partition randomized oracle", RegressionSuite::fitRandomized);
        execute("Partition deallocation and validation", RegressionSuite::fitDeallocation);
        execute("Fixed partition MFT", RegressionSuite::mftKnown);
        execute("MFT randomized oracle", RegressionSuite::mftRandomized);
        execute("Variable partition MVT", RegressionSuite::mvtKnown);
        execute("MVT randomized oracle", RegressionSuite::mvtRandomized);
        execute("Memory validation", RegressionSuite::memoryValidation);
        execute("Priority scheduling known order", RegressionSuite::schedulingKnown);
        execute("Priority scheduling randomized order", RegressionSuite::schedulingRandomized);
        execute("Priority scheduling validation", RegressionSuite::schedulingValidation);
        execute("Bounded buffer sequential", RegressionSuite::bufferSequential);
        execute("Bounded buffer concurrent stress", RegressionSuite::bufferConcurrent);
        execute("Bounded buffer interrupt restoration", RegressionSuite::bufferInterrupt);
        execute("Producer/consumer demo", RegressionSuite::producerConsumer);
        execute("Dining philosophers finite completion", RegressionSuite::diningPhilosophers);
        execute("CLI interface", RegressionSuite::cli);
        System.out.printf("PASS: %d groups, %d assertions%n", groups, checks);
    }

    private static void execute(String name, CheckedTest task) throws Exception {
        task.run();
        groups++;
        System.out.println("PASS: " + name);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static void eq(Object expected, Object actual) {
        check(Objects.equals(expected, actual), "Expected=" + expected + ", actual=" + actual);
    }

    private static void near(double expected, double actual) {
        check(Math.abs(expected - actual) < 1e-9, "Expected=" + expected + ", actual=" + actual);
    }

    private static void throwsException(Class<? extends Throwable> type, Runnable runnable) {
        checks++;
        try {
            runnable.run();
        } catch (Throwable ex) {
            if (type.isInstance(ex)) { return; }
            throw new AssertionError("Wrong exception: " + ex, ex);
        }
        throw new AssertionError("Expected exception: " + type.getSimpleName());
    }

    private static void lruOriginal() {
        SimulationResult result = new LruPageReplacement(4)
                .simulate(Arrays.asList(2, 2, 1, 4, 5, 2, 5, 6, 5, 7));
        eq(4, result.getHitCount());
        eq(6, result.getFaultCount());
        eq(Arrays.asList(2, 6, 7, 5), result.getFinalFrames());
        eq(Integer.valueOf(1), result.getAccesses().get(7).getEvictedPage());
        eq(Integer.valueOf(4), result.getAccesses().get(9).getEvictedPage());
        near(0.6, result.getFaultRate());
    }

    private static void lruRandomized() {
        Random random = new Random(20261122L);
        for (int c = 0; c < 800; c++) {
            int cap = random.nextInt(7) + 1;
            int length = random.nextInt(60);
            List<Integer> input = new ArrayList<>();
            for (int i = 0; i < length; i++) { input.add(random.nextInt(12)); }
            SimulationResult actual = new LruPageReplacement(cap).simulate(input);
            Map<Integer, Integer> resident = new LinkedHashMap<>(16, 0.75f, true);
            int faults = 0;
            for (int i = 0; i < input.size(); i++) {
                int page = input.get(i);
                Integer frame = resident.get(page);
                Integer victim = null;
                if (frame == null) {
                    faults++;
                    if (resident.size() < cap) {
                        frame = 0;
                        while (resident.containsValue(frame)) { frame++; }
                    } else {
                        Iterator<Map.Entry<Integer, Integer>> iterator = resident.entrySet().iterator();
                        Map.Entry<Integer, Integer> eldest = iterator.next();
                        victim = eldest.getKey();
                        frame = eldest.getValue();
                        iterator.remove();
                    }
                    resident.put(page, frame);
                }
                PageAccess step = actual.getAccesses().get(i);
                eq(frame.intValue(), step.getFrameIndex());
                eq(victim, step.getEvictedPage());
                eq(victim != null ? PageAccess.Outcome.FAULT_REPLACE
                        : (step.isHit() ? PageAccess.Outcome.HIT : PageAccess.Outcome.FAULT_LOAD),
                        step.getOutcome());
                List<Integer> expectedFrames = new ArrayList<>(Collections.nCopies(cap, null));
                for (Map.Entry<Integer, Integer> entry : resident.entrySet()) {
                    expectedFrames.set(entry.getValue(), entry.getKey());
                }
                eq(expectedFrames, step.getFrames());
            }
            eq(faults, actual.getFaultCount());
            eq(input.size() - faults, actual.getHitCount());
        }
    }

    private static void fifoOriginal() {
        SimulationResult fifo = new FifoPageReplacement(3)
                .simulate(Arrays.asList(1, 2, 3, 1, 4, 2, 5));
        eq(2, fifo.getHitCount());
        eq(5, fifo.getFaultCount());
        eq(Integer.valueOf(1), fifo.getAccesses().get(4).getEvictedPage());
        eq(Integer.valueOf(2), fifo.getAccesses().get(6).getEvictedPage());
        eq(Arrays.asList(4, 5, 3), fifo.getFinalFrames());
    }

    private static void fifoRandomized() {
        Random random = new Random(845771L);
        for (int c = 0; c < 800; c++) {
            int cap = random.nextInt(8) + 1;
            int length = random.nextInt(65);
            List<Integer> pages = new ArrayList<>();
            for (int i = 0; i < length; i++) { pages.add(random.nextInt(15)); }
            SimulationResult actual = new FifoPageReplacement(cap).simulate(pages);
            LinkedList<Integer> fifo = new LinkedList<>();
            int faults = 0;
            for (int i = 0; i < pages.size(); i++) {
                int page = pages.get(i);
                Integer victim = null;
                boolean hit = fifo.contains(page);
                if (!hit) {
                    faults++;
                    if (fifo.size() == cap) { victim = fifo.removeFirst(); }
                    fifo.addLast(page);
                }
                eq(victim, actual.getAccesses().get(i).getEvictedPage());
                eq(hit, actual.getAccesses().get(i).isHit());
            }
            eq(faults, actual.getFaultCount());
            eq(pages.size() - faults, actual.getHitCount());
        }
    }

    private static void pageValidation() {
        throwsException(IllegalArgumentException.class, () -> new FifoPageReplacement(0));
        throwsException(IllegalArgumentException.class, () -> new LruPageReplacement(-1));
        throwsException(NullPointerException.class, () -> new FifoPageReplacement(2).simulate(null));
        throwsException(NullPointerException.class, () -> new FifoPageReplacement(2).simulate(Arrays.asList(1, null)));
        throwsException(IllegalArgumentException.class, () -> new FifoPageReplacement(2).simulate(Arrays.asList(-2)));
        SimulationResult result = new FifoPageReplacement(2).simulate(Arrays.asList(1, 2, 3));
        throwsException(UnsupportedOperationException.class, () -> result.getFinalFrames().set(0, 99));
        throwsException(UnsupportedOperationException.class, () -> result.getAccesses().clear());
        eq(Arrays.asList(1, null), result.getAccesses().get(0).getFrames());
        eq(0, new FifoPageReplacement(2).simulate(Collections.emptyList()).getRequestCount());
    }

    private static void fitKnown() {
        int[] blocks = {100, 50, 200, 400, 150};
        int[] processes = {70, 110, 45, 400, 600, 140};
        int[][] expected = {{0, 2, 1, 3, -1, 4}, {0, 4, 1, 3, -1, 2},
                {3, 2, 4, -1, -1, -1}};
        int i = 0;
        for (FitStrategy strategy : FitStrategy.values()) {
            PartitionAllocator allocator = new PartitionAllocator(blocks, strategy);
            for (int p = 0; p < processes.length; p++) {
                eq(expected[i][p], allocator.allocate(processes[p]));
            }
            i++;
        }
    }

    private static void fitRandomized() {
        Random random = new Random(97610L);
        for (int caseNo = 0; caseNo < 500; caseNo++) {
            int n = 1 + random.nextInt(12);
            int[] blocks = new int[n];
            for (int i = 0; i < n; i++) { blocks[i] = 1 + random.nextInt(200); }
            for (FitStrategy strategy : FitStrategy.values()) {
                PartitionAllocator allocator = new PartitionAllocator(blocks, strategy);
                boolean[] used = new boolean[n];
                for (int r = 0; r < 30; r++) {
                    int size = random.nextInt(210) + 1;
                    int expected = -1;
                    for (int i = 0; i < n; i++) {
                        if (used[i] || blocks[i] < size) { continue; }
                        if (expected == -1 || (strategy == FitStrategy.BEST_FIT && blocks[i] < blocks[expected])
                                || (strategy == FitStrategy.WORST_FIT && blocks[i] > blocks[expected])) {
                            expected = i;
                        }
                        if (strategy == FitStrategy.FIRST_FIT) { break; }
                    }
                    eq(expected, allocator.allocate(size));
                    if (expected >= 0) { used[expected] = true; }
                }
            }
        }
    }

    private static void fitDeallocation() {
        PartitionAllocator allocator = new PartitionAllocator(new int[]{200, 100}, FitStrategy.BEST_FIT);
        eq(1, allocator.allocate(80));
        eq(20L, allocator.getInternalFragmentationBytes());
        eq(false, allocator.deallocate(0));
        eq(true, allocator.deallocate(1));
        eq(0L, allocator.getInternalFragmentationBytes());
        eq(1, allocator.allocate(100));
        eq(Arrays.asList(200), allocator.getFreePartitionSizes());
        throwsException(IllegalArgumentException.class, () -> allocator.allocate(0));
        throwsException(IndexOutOfBoundsException.class, () -> allocator.deallocate(-1));
        throwsException(UnsupportedOperationException.class, () -> allocator.getFreePartitionSizes().add(10));
    }

    private static void mftKnown() {
        FixedPartitionSimulator.Result result = new FixedPartitionSimulator()
                .simulate(1024, 256, Arrays.asList(120, 300, 200, 64, 80));
        eq(Arrays.asList(true, false, true, true, true), result.getAllocationDecisions());
        eq(4, result.getPartitionCount());
        eq(4, result.getAllocatedCount());
        eq(560L, result.getInternalFragmentationBytes());
        eq(0, result.getUnpartitionedBytes());
        eq(4, new FixedPartitionSimulator().simulate(10, 2,
                Arrays.asList(2, 2, 2, 2)).getAllocatedCount());
        eq(1, new FixedPartitionSimulator().simulate(5, 2,
                Arrays.asList(1)).getUnpartitionedBytes());
    }

    private static void mftRandomized() {
        Random random = new Random(681L);
        for (int test = 0; test < 500; test++) {
            int memory = random.nextInt(2000);
            int block = random.nextInt(200) + 1;
            List<Integer> processes = new ArrayList<>();
            for (int i = 0; i < 30; i++) { processes.add(random.nextInt(300) + 1); }
            FixedPartitionSimulator.Result result = new FixedPartitionSimulator().simulate(memory, block, processes);
            int capacity = memory / block;
            int used = 0;
            long internal = 0;
            for (int i = 0; i < processes.size(); i++) {
                int p = processes.get(i);
                boolean fits = p <= block && used < capacity;
                eq(fits, result.getAllocationDecisions().get(i));
                if (fits) { used++; internal += block - p; }
            }
            eq(used, result.getAllocatedCount());
            eq(internal, result.getInternalFragmentationBytes());
        }
    }

    private static void mvtKnown() {
        VariablePartitionSimulator.Result report = new VariablePartitionSimulator()
                .simulate(500, Arrays.asList(140, 220, 200, 100));
        eq(Arrays.asList(true, true, false, true), report.getAllocationDecisions());
        eq(460L, report.getAllocatedBytes());
        eq(40L, report.getFreeBytes());
        eq(0L, new VariablePartitionSimulator().simulate(0, Collections.emptyList()).getFreeBytes());
    }

    private static void mvtRandomized() {
        Random random = new Random(13822L);
        for (int c = 0; c < 500; c++) {
            int memory = random.nextInt(2000);
            List<Integer> input = new ArrayList<>();
            for (int i = 0; i < 30; i++) { input.add(random.nextInt(400) + 1); }
            VariablePartitionSimulator.Result actual = new VariablePartitionSimulator().simulate(memory, input);
            long free = memory;
            for (int i = 0; i < input.size(); i++) {
                boolean accepted = input.get(i) <= free;
                eq(accepted, actual.getAllocationDecisions().get(i));
                if (accepted) { free -= input.get(i); }
            }
            eq(free, actual.getFreeBytes());
            eq(memory - free, actual.getAllocatedBytes());
        }
    }

    private static void memoryValidation() {
        throwsException(IllegalArgumentException.class, () -> new PartitionAllocator(new int[]{10, 0}, FitStrategy.FIRST_FIT));
        throwsException(NullPointerException.class, () -> new PartitionAllocator(null, FitStrategy.FIRST_FIT));
        throwsException(NullPointerException.class, () -> new PartitionAllocator(new int[]{5}, null));
        throwsException(IllegalArgumentException.class, () -> new FixedPartitionSimulator().simulate(10, 0, Collections.emptyList()));
        throwsException(IllegalArgumentException.class, () -> new FixedPartitionSimulator().simulate(-1, 1, Collections.emptyList()));
        throwsException(IllegalArgumentException.class, () -> new FixedPartitionSimulator().simulate(10, 5, Arrays.asList(0)));
        throwsException(IllegalArgumentException.class, () -> new VariablePartitionSimulator().simulate(-1, Collections.emptyList()));
        throwsException(IllegalArgumentException.class, () -> new VariablePartitionSimulator().simulate(10, Arrays.asList(-2)));
        throwsException(NullPointerException.class, () -> new VariablePartitionSimulator().simulate(10, null));
    }

    private static void schedulingKnown() {
        PriorityScheduler.Result result = new PriorityScheduler().schedule(Arrays.asList(
                new ProcessSpec("P1", 5, 1), new ProcessSpec("P2", 3, 3),
                new ProcessSpec("P3", 2, 2), new ProcessSpec("P4", 4, 3)));
        eq(Arrays.asList("P2", "P4", "P3", "P1"), ids(result.getEntries()));
        eq(0L, result.getEntries().get(0).getWaitingTime());
        eq(3L, result.getEntries().get(1).getWaitingTime());
        near(4.75, result.getAverageWaitingTime());
        near(8.25, result.getAverageTurnaroundTime());
        eq(Arrays.asList("Z", "A"), ids(new PriorityScheduler().schedule(Arrays.asList(
                new ProcessSpec("Z", 1, 0), new ProcessSpec("A", 1, -1))).getEntries()));
        near(0, new PriorityScheduler().schedule(Collections.emptyList()).getAverageWaitingTime());
    }

    private static List<String> ids(List<PriorityScheduler.Entry> entries) {
        List<String> result = new ArrayList<>();
        for (PriorityScheduler.Entry entry : entries) { result.add(entry.getProcess().getId()); }
        return result;
    }

    private static void schedulingRandomized() {
        Random random = new Random(78899L);
        for (int c = 0; c < 800; c++) {
            List<ProcessSpec> input = new ArrayList<>();
            for (int i = 0; i < random.nextInt(40) + 1; i++) {
                input.add(new ProcessSpec("P" + i, 1 + random.nextInt(100), random.nextInt(11) - 5));
            }
            PriorityScheduler.Result actual = new PriorityScheduler().schedule(input);
            int previousPriority = Integer.MAX_VALUE;
            long nextStart = 0;
            for (PriorityScheduler.Entry entry : actual.getEntries()) {
                check(entry.getProcess().getPriority() <= previousPriority, "Priority order");
                previousPriority = entry.getProcess().getPriority();
                eq(nextStart, entry.getWaitingTime());
                nextStart += entry.getProcess().getBurstTime();
                eq(nextStart, entry.getTurnaroundTime());
            }
            eq(input.size(), actual.getEntries().size());
        }
    }

    private static void schedulingValidation() {
        throwsException(IllegalArgumentException.class, () -> new ProcessSpec("", 1, 0));
        throwsException(IllegalArgumentException.class, () -> new ProcessSpec("P", 0, 0));
        throwsException(IllegalArgumentException.class, () -> new PriorityScheduler().schedule(
                Arrays.asList(new ProcessSpec("P", 1, 0), new ProcessSpec("P", 2, 1))));
        throwsException(NullPointerException.class, () -> new PriorityScheduler().schedule(null));
        throwsException(UnsupportedOperationException.class, () ->
                new PriorityScheduler().schedule(Collections.emptyList()).getEntries().add(null));
    }

    private static void bufferSequential() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);
        eq(2, buffer.getCapacity());
        eq(0, buffer.size());
        buffer.put(10);
        buffer.put(20);
        eq(2, buffer.size());
        eq(10, buffer.take());
        eq(20, buffer.take());
        eq(0, buffer.size());
        throwsException(IllegalArgumentException.class, () -> new BoundedBuffer<>(0));
        throwsException(NullPointerException.class, () -> {
            try { buffer.put(null); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
        });
    }

    private static void bufferConcurrent() throws Exception {
        final int producers = 4;
        final int each = 180;
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(5);
        List<Integer> output = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] threads = new Thread[producers + 3];
        for (int p = 0; p < producers; p++) {
            final int source = p;
            threads[p] = new Thread(() -> {
                try {
                    for (int i = 0; i < each; i++) { buffer.put(source * each + i); }
                } catch (Throwable ex) { error.compareAndSet(null, ex); }
            });
        }
        for (int c = 0; c < 3; c++) {
            final int consumer = c;
            threads[producers + c] = new Thread(() -> {
                try {
                    for (int i = consumer; i < producers * each; i += 3) { output.add(buffer.take()); }
                } catch (Throwable ex) { error.compareAndSet(null, ex); }
            });
        }
        for (Thread t : threads) { t.start(); }
        for (Thread t : threads) { t.join(7_000); }
        for (Thread t : threads) { check(!t.isAlive(), "Worker did not finish"); }
        eq(null, error.get());
        eq(producers * each, output.size());
        eq(producers * each, new HashSet<>(output).size());
        eq(0, buffer.size());
    }

    private static void bufferInterrupt() throws Exception {
        BoundedBuffer<Integer> empty = new BoundedBuffer<>(1);
        AtomicReference<Throwable> observed = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread waiting = new Thread(() -> {
            started.countDown();
            try { empty.take(); }
            catch (InterruptedException ex) { observed.set(ex); }
        });
        waiting.start();
        check(started.await(1, TimeUnit.SECONDS), "Worker never started");
        waiting.interrupt();
        waiting.join(1000);
        check(!waiting.isAlive(), "Interrupted consumer still alive");
        check(observed.get() instanceof InterruptedException, "Consumer ignored interrupt");
        empty.put(10);
        eq(10, empty.take());
        BoundedBuffer<Integer> full = new BoundedBuffer<>(1);
        full.put(1);
        CountDownLatch startedAgain = new CountDownLatch(1);
        Thread blockedProducer = new Thread(() -> {
            startedAgain.countDown();
            try { full.put(2); } catch (InterruptedException ex) { observed.set(ex); }
        });
        blockedProducer.start();
        check(startedAgain.await(1, TimeUnit.SECONDS), "Producer never started");
        blockedProducer.interrupt();
        blockedProducer.join(1000);
        check(!blockedProducer.isAlive(), "Interrupted producer still alive");
        eq(1, full.take());
        full.put(3);
        eq(3, full.take());
    }

    private static void producerConsumer() throws Exception {
        List<String> values = Arrays.asList("a", "b", "c", "d", "e");
        for (int capacity = 1; capacity <= 6; capacity++) {
            eq(values, ProducerConsumerDemo.run(values, capacity));
        }
        eq(Collections.emptyList(), ProducerConsumerDemo.run(Collections.emptyList(), 1));
        throwsException(IllegalArgumentException.class, () -> {
            try { ProducerConsumerDemo.run(values, 0); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
        });
    }

    private static void diningPhilosophers() throws Exception {
        DiningPhilosophersSimulator simulator = new DiningPhilosophersSimulator();
        for (int n = 2; n <= 9; n++) {
            for (int rounds : new int[]{0, 1, 4, 25}) {
                int[] meals = simulator.simulate(n, rounds);
                eq(n, meals.length);
                for (int consumed : meals) { eq(rounds, consumed); }
            }
        }
        throwsException(IllegalArgumentException.class, () -> {
            try { simulator.simulate(1, 1); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
        });
    }

    private static void cli() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout);
        PrintStream err = new PrintStream(stderr);
        eq(0, App.run(new String[]{"help"}, out, err));
        check(stdout.toString().contains("Commands"), "Help does not list modules");
        stdout.reset();
        eq(0, App.run(new String[]{"fifo", "3", "1,2,3,1,4"}, out, err));
        check(stdout.toString().contains("Hits=1, Faults=4"), "FIFO CLI counts");
        stdout.reset();
        eq(0, App.run(new String[]{"lru", "1", ""}, out, err));
        check(stdout.toString().contains("Hits=0, Faults=0"), "Empty paging trace");
        eq(2, App.run(new String[]{"fifo", "0", "1"}, out, err));
        eq(2, App.run(new String[]{"unrecognized"}, out, err));
        stdout.reset();
        eq(0, App.run(new String[]{"all"}, out, err));
        check(stdout.toString().contains("Dining Philosophers"), "Combined CLI demo");
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run() throws Exception;
    }
}

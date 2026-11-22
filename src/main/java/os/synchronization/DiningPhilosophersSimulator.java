package os.synchronization;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Finite dining-philosophers simulation using globally ordered fork acquisition.
 * All philosophers lock the lower-numbered fork before the higher-numbered fork,
 * breaking circular wait and therefore preventing deadlock. This does not
 * establish starvation freedom in a general unbounded execution.
 */
public final class DiningPhilosophersSimulator {
    /**
     * Starts one thread per philosopher and returns meal counts after joining.
     *
     * @param philosopherCount at least two philosophers
     * @param rounds non-negative number of meals to eat per philosopher
     * @throws InterruptedException when the calling thread is interrupted
     */
    public int[] simulate(int philosopherCount, int rounds) throws InterruptedException {
        if (philosopherCount < 2 || rounds < 0) {
            throw new IllegalArgumentException("Need at least two philosophers and non-negative rounds.");
        }
        Object[] forks = new Object[philosopherCount];
        Arrays.setAll(forks, i -> new Object());
        AtomicIntegerArray meals = new AtomicIntegerArray(philosopherCount);
        Thread[] workers = new Thread[philosopherCount];
        for (int i = 0; i < philosopherCount; i++) {
            final int philosopher = i;
            int a = i;
            int b = (i + 1) % philosopherCount;
            Object first = forks[Math.min(a, b)];
            Object second = forks[Math.max(a, b)];
            workers[i] = new Thread(() -> {
                for (int meal = 0; meal < rounds && !Thread.currentThread().isInterrupted(); meal++) {
                    synchronized (first) {
                        synchronized (second) {
                            meals.incrementAndGet(philosopher);
                        }
                    }
                    Thread.yield();
                }
            }, "philosopher-" + i);
        }
        for (Thread thread : workers) {
            thread.start();
        }
        try {
            for (Thread thread : workers) {
                thread.join(10_000);
            }
            for (Thread thread : workers) {
                if (thread.isAlive()) {
                    throw new IllegalStateException("Philosophers did not finish before timeout.");
                }
            }
        } finally {
            for (Thread thread : workers) {
                thread.interrupt();
            }
            for (Thread thread : workers) {
                if (thread.isAlive()) { thread.join(1000); }
            }
        }
        int[] totals = new int[philosopherCount];
        for (int i = 0; i < philosopherCount; i++) {
            totals[i] = meals.get(i);
        }
        return totals;
    }
}

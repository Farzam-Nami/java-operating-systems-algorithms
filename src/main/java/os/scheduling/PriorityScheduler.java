package os.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Non-preemptive priority scheduling with every process arriving at time zero.
 * Higher numbers mean higher priority. Arrival/input order breaks ties.
 */
public final class PriorityScheduler {
    /**
     * Schedules each process once and computes waiting/turnaround times.
     *
     * @param processes input order is the stable tie-breaking order
     * @return immutable timeline and average waiting/turnaround times
     */
    public Result schedule(List<ProcessSpec> processes) {
        Objects.requireNonNull(processes, "Processes must not be null.");
        List<ProcessSpec> ordered = new ArrayList<>(processes);
        Set<String> ids = new HashSet<>();
        for (ProcessSpec process : ordered) {
            Objects.requireNonNull(process, "Process entry must not be null.");
            if (!ids.add(process.getId())) {
                throw new IllegalArgumentException("Duplicate process ID: " + process.getId());
            }
        }
        // Java's stable sort preserves input order when priorities are equal.
        ordered.sort(Comparator.comparingInt(ProcessSpec::getPriority).reversed());
        long currentTime = 0;
        long totalWaiting = 0;
        long totalTurnaround = 0;
        List<Entry> timeline = new ArrayList<>();
        for (ProcessSpec process : ordered) {
            long start = currentTime;
            currentTime += process.getBurstTime();
            timeline.add(new Entry(process, start, currentTime));
            totalWaiting += start;
            totalTurnaround += currentTime;
        }
        int count = ordered.size();
        return new Result(timeline, count == 0 ? 0 : (double) totalWaiting / count,
                count == 0 ? 0 : (double) totalTurnaround / count);
    }

    /** Completed process with starting and completion times. */
    public static final class Entry {
        private final ProcessSpec process;
        private final long startTime;
        private final long completionTime;

        private Entry(ProcessSpec process, long startTime, long completionTime) {
            this.process = process;
            this.startTime = startTime;
            this.completionTime = completionTime;
        }

        /** Scheduled process specification. */
        public ProcessSpec getProcess() { return process; }

        /** Start time; also waiting time because all processes arrive at zero. */
        public long getStartTime() { return startTime; }

        /** Completion time; also turnaround time for zero arrival times. */
        public long getCompletionTime() { return completionTime; }

        /** Time spent waiting before the process begins. */
        public long getWaitingTime() { return startTime; }

        /** Time between arrival (zero) and completion. */
        public long getTurnaroundTime() { return completionTime; }
    }

    /** Immutable schedule and per-process averages. */
    public static final class Result {
        private final List<Entry> entries;
        private final double averageWaitingTime;
        private final double averageTurnaroundTime;

        private Result(List<Entry> entries, double waiting, double turnaround) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.averageWaitingTime = waiting;
            this.averageTurnaroundTime = turnaround;
        }

        /** Processes in actual execution order. */
        public List<Entry> getEntries() { return entries; }

        /** Mean wait before CPU execution; 0.0 for an empty schedule. */
        public double getAverageWaitingTime() { return averageWaitingTime; }

        /** Mean completion time relative to arrival at zero. */
        public double getAverageTurnaroundTime() { return averageTurnaroundTime; }
    }
}

package os.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Models contiguous, sequential variable-size allocations (MVT).
 * Because there is no deallocation, unused tail memory is free space, not
 * external fragmentation; this model intentionally does not simulate holes.
 */
public final class VariablePartitionSimulator {
    /**
     * Places each positive-size process in the remaining contiguous tail if it fits.
     * A rejected process does not prevent subsequent smaller processes from fitting.
     */
    public Result simulate(int totalBytes, List<Integer> processes) {
        if (totalBytes < 0) {
            throw new IllegalArgumentException("Total memory must be non-negative.");
        }
        Objects.requireNonNull(processes, "Processes must not be null.");
        long remaining = totalBytes;
        List<Boolean> decisions = new ArrayList<>();
        for (Integer process : processes) {
            Objects.requireNonNull(process, "Process size must not be null.");
            if (process <= 0) {
                throw new IllegalArgumentException("Process sizes must be positive.");
            }
            boolean accepted = process <= remaining;
            decisions.add(accepted);
            if (accepted) {
                remaining -= process;
            }
        }
        return new Result(decisions, totalBytes - remaining, remaining);
    }

    /** Immutable decisions and space utilization. */
    public static final class Result {
        private final List<Boolean> decisions;
        private final long allocatedBytes;
        private final long freeBytes;

        private Result(List<Boolean> decisions, long allocatedBytes, long freeBytes) {
            this.decisions = Collections.unmodifiableList(new ArrayList<>(decisions));
            this.allocatedBytes = allocatedBytes;
            this.freeBytes = freeBytes;
        }

        /** Allocation decision in arrival order. */
        public List<Boolean> getAllocationDecisions() { return decisions; }

        /** Sum of sizes of accepted processes. */
        public long getAllocatedBytes() { return allocatedBytes; }

        /** Remaining contiguous free tail; not external fragmentation. */
        public long getFreeBytes() { return freeBytes; }
    }
}

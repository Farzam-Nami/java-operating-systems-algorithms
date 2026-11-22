package os.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Simulates multiprogramming with a fixed number of equal-sized partitions (MFT).
 * Processes arrive sequentially and remain resident throughout the simulation.
 */
public final class FixedPartitionSimulator {
    /** Runs an MFT allocation with no deallocations. */
    public Result simulate(int totalBytes, int blockBytes, List<Integer> processes) {
        if (totalBytes < 0 || blockBytes <= 0) {
            throw new IllegalArgumentException("Memory must be non-negative and block size positive.");
        }
        Objects.requireNonNull(processes, "Processes must not be null.");
        int blocks = totalBytes / blockBytes;
        int allocated = 0;
        long internal = 0;
        List<Boolean> decisions = new ArrayList<>();
        for (Integer process : processes) {
            Objects.requireNonNull(process, "Process size must not be null.");
            if (process <= 0) {
                throw new IllegalArgumentException("Process sizes must be positive.");
            }
            boolean fits = process <= blockBytes && allocated < blocks;
            decisions.add(fits);
            if (fits) {
                allocated++;
                internal += blockBytes - process;
            }
        }
        return new Result(decisions, allocated, blocks, internal,
                totalBytes % blockBytes);
    }

    /** Immutable allocation decisions and wasted-space accounting. */
    public static final class Result {
        private final List<Boolean> decisions;
        private final int allocatedCount;
        private final int partitionCount;
        private final long internalFragmentation;
        private final int unpartitionedBytes;

        private Result(List<Boolean> decisions, int allocatedCount, int partitionCount,
                       long internalFragmentation, int unpartitionedBytes) {
            this.decisions = Collections.unmodifiableList(new ArrayList<>(decisions));
            this.allocatedCount = allocatedCount;
            this.partitionCount = partitionCount;
            this.internalFragmentation = internalFragmentation;
            this.unpartitionedBytes = unpartitionedBytes;
        }

        /** True for each process that fits into an available partition. */
        public List<Boolean> getAllocationDecisions() { return decisions; }

        /** Number of accepted processes. */
        public int getAllocatedCount() { return allocatedCount; }

        /** Number of equal-sized partitions constructed from total memory. */
        public int getPartitionCount() { return partitionCount; }

        /** Unused bytes inside allocated partitions. */
        public long getInternalFragmentationBytes() { return internalFragmentation; }

        /** Remainder that is too small to form a complete partition. */
        public int getUnpartitionedBytes() { return unpartitionedBytes; }
    }
}

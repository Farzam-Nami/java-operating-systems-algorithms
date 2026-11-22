package os.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Allocates at most one process per fixed partition using a chosen fit policy.
 * Unlike variable-size heap allocation, partitions are not split or coalesced.
 */
public final class PartitionAllocator {
    private final int[] capacities;
    private final int[] usedBytes;
    private final FitStrategy strategy;

    /**
     * @param partitionSizes positive partition capacities, in bytes
     * @param strategy selection policy for a fitting free partition
     */
    public PartitionAllocator(int[] partitionSizes, FitStrategy strategy) {
        Objects.requireNonNull(partitionSizes, "Partition sizes must not be null.");
        this.strategy = Objects.requireNonNull(strategy, "Strategy must not be null.");
        this.capacities = partitionSizes.clone();
        this.usedBytes = new int[partitionSizes.length];
        for (int capacity : capacities) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("All partition sizes must be positive.");
            }
        }
    }

    /**
     * Allocates a process to a free partition or returns -1 if no partition fits.
     * Ties are resolved by the lowest partition index.
     *
     * @param processBytes strictly positive process size
     * @return chosen partition index, or -1 when no allocation is possible
     */
    public int allocate(int processBytes) {
        if (processBytes <= 0) {
            throw new IllegalArgumentException("Process size must be positive.");
        }
        int chosen = -1;
        for (int index = 0; index < capacities.length; index++) {
            if (usedBytes[index] != 0 || capacities[index] < processBytes) {
                continue;
            }
            if (chosen == -1 || (strategy == FitStrategy.BEST_FIT && capacities[index] < capacities[chosen])
                    || (strategy == FitStrategy.WORST_FIT && capacities[index] > capacities[chosen])) {
                chosen = index;
            }
            if (strategy == FitStrategy.FIRST_FIT) {
                break;
            }
        }
        if (chosen != -1) {
            usedBytes[chosen] = processBytes;
        }
        return chosen;
    }

    /** Releases the occupied partition at index; returns false if already free. */
    public boolean deallocate(int index) {
        checkIndex(index);
        if (usedBytes[index] == 0) {
            return false;
        }
        usedBytes[index] = 0;
        return true;
    }

    /** Returns the process's size in a partition, or zero if the partition is free. */
    public int getAllocatedBytes(int index) {
        checkIndex(index);
        return usedBytes[index];
    }

    /** Returns the total unused space *inside* occupied partitions. */
    public long getInternalFragmentationBytes() {
        long fragmentation = 0;
        for (int index = 0; index < capacities.length; index++) {
            if (usedBytes[index] != 0) {
                fragmentation += capacities[index] - usedBytes[index];
            }
        }
        return fragmentation;
    }

    /** Returns the capacities of free partitions, not internal fragmentation. */
    public List<Integer> getFreePartitionSizes() {
        List<Integer> free = new ArrayList<>();
        for (int index = 0; index < capacities.length; index++) {
            if (usedBytes[index] == 0) {
                free.add(capacities[index]);
            }
        }
        return Collections.unmodifiableList(free);
    }

    /** Returns the number of partitions. */
    public int getPartitionCount() {
        return capacities.length;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacities.length) {
            throw new IndexOutOfBoundsException("Invalid partition index: " + index);
        }
    }
}

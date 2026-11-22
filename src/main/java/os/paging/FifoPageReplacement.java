package os.paging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Simulates first-in-first-out (FIFO) page replacement.
 * A page hit does not change the order in which resident pages were loaded.
 */
public final class FifoPageReplacement {
    private final int frameCount;

    /** Constructs a simulator with a positive number of physical frames. */
    public FifoPageReplacement(int frameCount) {
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Frame count must be positive.");
        }
        this.frameCount = frameCount;
    }

    /** Returns the configured frame count. */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Records one immutable snapshot per reference, along with hits, faults,
     * and the evicted page (if any). Empty frames are represented by null.
     *
     * @param references page identifiers in request order; values must be non-null and non-negative
     * @return a report with a complete trace and aggregate statistics
     */
    public SimulationResult simulate(List<Integer> references) {
        Objects.requireNonNull(references, "Page references must not be null.");
        Integer[] frames = new Integer[frameCount];
        int nextToReplace = 0;
        int hits = 0;
        int faults = 0;
        List<PageAccess> accesses = new ArrayList<>();

        for (int step = 0; step < references.size(); step++) {
            Integer requested = Objects.requireNonNull(references.get(step),
                    "Page reference at index " + step + " must not be null.");
            if (requested < 0) {
                throw new IllegalArgumentException("Page IDs must be non-negative.");
            }
            int slot = -1;
            for (int index = 0; index < frameCount; index++) {
                if (requested.equals(frames[index])) {
                    slot = index;
                    break;
                }
            }
            PageAccess.Outcome outcome;
            Integer evicted = null;
            if (slot >= 0) {
                hits++;
                outcome = PageAccess.Outcome.HIT;
            } else {
                faults++;
                slot = nextToReplace;
                evicted = frames[slot];
                outcome = evicted == null ? PageAccess.Outcome.FAULT_LOAD
                        : PageAccess.Outcome.FAULT_REPLACE;
                frames[slot] = requested;
                nextToReplace = (nextToReplace + 1) % frameCount;
            }
            accesses.add(new PageAccess(step, requested, outcome, slot, evicted,
                    Arrays.asList(frames.clone())));
        }
        return new SimulationResult(frameCount, hits, faults, accesses,
                Arrays.asList(frames.clone()));
    }
}

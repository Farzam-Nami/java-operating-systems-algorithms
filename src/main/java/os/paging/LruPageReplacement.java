package os.paging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Simulates least recently used (LRU) page replacement in a fixed number of frames.
 * Each simulation starts with empty frames and maintains its own access history.
 */
public final class LruPageReplacement {
    private static final int EMPTY_FRAME = -1;

    private final int frameCount;

    /**
     * @param frameCount number of available physical frames; must be positive
     * @throws IllegalArgumentException if {@code frameCount} is not positive
     */
    public LruPageReplacement(int frameCount) {
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Frame count must be positive.");
        }
        this.frameCount = frameCount;
    }

    /** Returns the configured number of physical frames. */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Processes page references in order, recording each access and the frame state.
     * A hit updates the page's recency. A fault uses the first free frame, or evicts
     * the resident page with the oldest access timestamp when every frame is occupied.
     *
     * @param pageReferences ordered, non-null list of non-null, non-negative page IDs
     * @return immutable simulation report; an empty sequence produces zero hits/faults
     * @throws NullPointerException if the list or any element is null
     * @throws IllegalArgumentException if a page ID is negative
     */
    public SimulationResult simulate(List<Integer> pageReferences) {
        Objects.requireNonNull(pageReferences, "Page reference list must not be null.");
        int[] frames = new int[frameCount];
        Arrays.fill(frames, EMPTY_FRAME);

        // Each timestamp is the index of the most recent access to that frame's page.
        long[] lastAccess = new long[frameCount];
        Arrays.fill(lastAccess, -1L);

        List<PageAccess> history = new ArrayList<>();
        int hits = 0;
        int faults = 0;

        for (int index = 0; index < pageReferences.size(); index++) {
            Integer requested = Objects.requireNonNull(
                    pageReferences.get(index), "Page ID at index " + index + " must not be null.");
            if (requested < 0) {
                throw new IllegalArgumentException("Page ID at index " + index + " must be non-negative.");
            }

            int frameIndex = findPage(frames, requested);
            PageAccess.Outcome outcome;
            Integer evictedPage = null;

            if (frameIndex >= 0) {
                hits++;
                outcome = PageAccess.Outcome.HIT;
            } else {
                faults++;
                frameIndex = findEmptyFrame(frames);
                if (frameIndex >= 0) {
                    outcome = PageAccess.Outcome.FAULT_LOAD;
                } else {
                    frameIndex = findLeastRecentlyUsedFrame(lastAccess);
                    evictedPage = frames[frameIndex];
                    outcome = PageAccess.Outcome.FAULT_REPLACE;
                }
                frames[frameIndex] = requested;
            }

            lastAccess[frameIndex] = index;
            history.add(new PageAccess(index, requested, outcome, frameIndex,
                    evictedPage, snapshot(frames)));
        }

        return new SimulationResult(frameCount, hits, faults, history, snapshot(frames));
    }

    /** Finds the frame holding a page, or -1 when the page is not resident. */
    private static int findPage(int[] frames, int pageId) {
        for (int index = 0; index < frames.length; index++) {
            if (frames[index] == pageId) {
                return index;
            }
        }
        return -1;
    }

    /** Returns the lowest-index free frame, or -1 when memory is full. */
    private static int findEmptyFrame(int[] frames) {
        return findPage(frames, EMPTY_FRAME);
    }

    /** Returns the frame least recently accessed; timestamps are unique for occupied frames. */
    private static int findLeastRecentlyUsedFrame(long[] lastAccess) {
        int oldest = 0;
        for (int index = 1; index < lastAccess.length; index++) {
            if (lastAccess[index] < lastAccess[oldest]) {
                oldest = index;
            }
        }
        return oldest;
    }

    /** Copies frame contents; null indicates an unoccupied frame in the public report. */
    private static List<Integer> snapshot(int[] frames) {
        List<Integer> copy = new ArrayList<>(frames.length);
        for (int page : frames) {
            copy.add(page == EMPTY_FRAME ? null : page);
        }
        return copy;
    }
}

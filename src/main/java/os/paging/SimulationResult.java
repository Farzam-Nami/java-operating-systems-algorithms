package os.paging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable aggregate statistics and trace from a page-replacement simulation. */
public final class SimulationResult {
    private final int frameCount;
    private final int hitCount;
    private final int faultCount;
    private final List<PageAccess> accesses;
    private final List<Integer> finalFrames;

    SimulationResult(int frameCount, int hitCount, int faultCount,
                     List<PageAccess> accesses, List<Integer> finalFrames) {
        this.frameCount = frameCount;
        this.hitCount = hitCount;
        this.faultCount = faultCount;
        this.accesses = Collections.unmodifiableList(new ArrayList<>(accesses));
        this.finalFrames = Collections.unmodifiableList(new ArrayList<>(finalFrames));
    }

    /** Number of frames allocated to this simulation. */
    public int getFrameCount() {
        return frameCount;
    }

    /** Number of page references processed. */
    public int getRequestCount() {
        return accesses.size();
    }

    /** Number of references served by pages already resident in memory. */
    public int getHitCount() {
        return hitCount;
    }

    /** Number of references that required loading a page into memory. */
    public int getFaultCount() {
        return faultCount;
    }

    /** Page faults divided by total accesses, or 0.0 for an empty input sequence. */
    public double getFaultRate() {
        return accesses.isEmpty() ? 0.0 : (double) faultCount / accesses.size();
    }

    /** Ordered, immutable per-request trace. */
    public List<PageAccess> getAccesses() {
        return accesses;
    }

    /** Immutable contents of physical frames after the final request. */
    public List<Integer> getFinalFrames() {
        return finalFrames;
    }
}

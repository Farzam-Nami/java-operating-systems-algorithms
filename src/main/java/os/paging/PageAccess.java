package os.paging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable record of one page request and the resulting memory state. */
public final class PageAccess {

    /** Distinguishes a hit, a fault using free space, and a fault requiring eviction. */
    public enum Outcome {
        HIT,
        FAULT_LOAD,
        FAULT_REPLACE
    }

    private final int requestIndex;
    private final int pageId;
    private final Outcome outcome;
    private final int frameIndex;
    private final Integer evictedPage;
    private final List<Integer> frames;

    PageAccess(int requestIndex, int pageId, Outcome outcome, int frameIndex,
               Integer evictedPage, List<Integer> frames) {
        this.requestIndex = requestIndex;
        this.pageId = pageId;
        this.outcome = Objects.requireNonNull(outcome, "Outcome must not be null.");
        this.frameIndex = frameIndex;
        this.evictedPage = evictedPage;
        this.frames = Collections.unmodifiableList(new ArrayList<>(frames));
    }

    /** Zero-based position of this reference within the input sequence. */
    public int getRequestIndex() {
        return requestIndex;
    }

    /** Page number requested by the process. */
    public int getPageId() {
        return pageId;
    }

    /** Kind of access: hit, fault with free space, or fault with replacement. */
    public Outcome getOutcome() {
        return outcome;
    }

    /** Index of the frame in which the requested page resides after this access. */
    public int getFrameIndex() {
        return frameIndex;
    }

    /** Evicted page number, or null when no replacement occurred. */
    public Integer getEvictedPage() {
        return evictedPage;
    }

    /** Immutable frame snapshot after processing the request; null denotes empty. */
    public List<Integer> getFrames() {
        return frames;
    }

    /** True only when the requested page was already resident. */
    public boolean isHit() {
        return outcome == Outcome.HIT;
    }

    /** True when the request caused a page fault. */
    public boolean isFault() {
        return outcome != Outcome.HIT;
    }
}

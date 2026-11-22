package os.scheduling;

import java.util.Objects;

/** CPU burst and priority for a process available at time zero. */
public final class ProcessSpec {
    private final String id;
    private final int burstTime;
    private final int priority;

    /** Creates one process; a greater numeric priority is scheduled sooner. */
    public ProcessSpec(String id, int burstTime, int priority) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Process ID must not be blank.");
        }
        if (burstTime <= 0) {
            throw new IllegalArgumentException("Burst time must be positive.");
        }
        this.id = id;
        this.burstTime = burstTime;
        this.priority = priority;
    }

    /** Unique process identifier within one scheduling run. */
    public String getId() { return id; }

    /** CPU time units needed for completion. */
    public int getBurstTime() { return burstTime; }

    /** Numeric priority; higher values run first, including zero and negatives. */
    public int getPriority() { return priority; }
}

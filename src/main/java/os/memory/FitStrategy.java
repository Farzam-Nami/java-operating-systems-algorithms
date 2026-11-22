package os.memory;

/** Policy for choosing among currently free fixed-size partitions. */
public enum FitStrategy {
    FIRST_FIT,
    BEST_FIT,
    WORST_FIT
}

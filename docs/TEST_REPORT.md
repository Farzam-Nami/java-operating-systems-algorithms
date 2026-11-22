# Verification Report

## Test commands

From the repository root:

```bash
./scripts/test.sh               # Pure JDK, no Maven dependencies
./scripts/run-demo.sh all       # Every demonstration
./scripts/run-demo.sh help      # CLI help
```

For users with Maven installed:

```bash
mvn clean verify
```

The GitHub Actions workflow also invokes `mvn --batch-mode verify` on Java 11 and Java 17.

## Local verification

- Main sources compiled using `javac --release 11`.
- The combined CLI command ran successfully for every module.
- The standalone regression runner passed **22 test groups with 246,372 assertions** at the time this package was assembled.
- Randomized tests use fixed seeds; their expected values are calculated independently of the implementations under test.

### Coverage

| Test group | Focus |
| --- | --- |
| LRU original exercise | Original 4-frame reference sequence, hits, faults, victims, final frames |
| LRU randomized oracle | 800 random cases compared with access-ordered map implementation |
| FIFO known traces | Hits do not update FIFO replacement order |
| FIFO randomized oracle | 800 random cases compared with independent resident FIFO queue |
| Page validation and immutability | Nulls, negatives, empty input, defensive reports |
| Partition fit policies | Deterministic First/Best/Worst Fit assignments |
| Partition randomized oracle | 500 random block sets for every fit strategy |
| Partition deallocation and validation | Reuse, occupied/free states, invalid indices |
| Fixed partition MFT | Block count, per-process decisions, unused capacity accounting |
| MFT randomized oracle | 500 random fixed-block scenarios |
| Variable partition MVT | Exact-fit allocations, rejections, free-tail count |
| MVT randomized oracle | 500 random variable-partition scenarios |
| Memory validation | Invalid sizes, lists, and strategy settings |
| Priority scheduling known order | Input order for ties, wait and turnaround averages |
| Priority scheduling randomized order | 800 random process sets, sorted priorities, contiguous timeline |
| Priority scheduling validation | Blank IDs, duplicates, non-positive bursts, nulls |
| Bounded buffer sequential | FIFO order, count, capacity, null rejection |
| Bounded buffer concurrent stress | Multiple producers and consumers; unique item delivery |
| Bounded buffer interrupt restoration | Cancellation of blocked `put` / `take`; no lost permits |
| Producer/consumer demo | Single-producer, single-consumer token ordering, empty sequence |
| Dining philosophers finite completion | 2–9 philosophers with multiple finite round counts |
| CLI interface | Help, optional paging arguments, invalid tokens and all-modules output |

## Limitations

1. The JDK standalone test script and demo were executed locally. The `mvn verify` workflow is configured but **Maven was not available in the local execution environment**. Its successful completion on GitHub Actions must be confirmed after pushing the repository.
2. The tests do not exhaustively explore every possible thread interleaving or prove real-time liveness.
3. MVT and priority scheduling deliberately implement simplified classroom models, as described in the API reference.
4. The original uploaded source files are preserved outside this publishable package, not in the GitHub ZIP.

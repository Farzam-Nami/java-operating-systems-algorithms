# API and Implementation Reference

This document describes the Java 11 implementation in `src/main/java`. Paths are relative to the repository root. All modules use the `os` package tree; there is no author-specific package prefix. Unless stated otherwise, **all collection outputs are immutable views/copies** and simulations do not mutate their caller's input lists or arrays.

## Table of contents

1. [Command-line application](#1-command-line-application)
2. [Page-replacement algorithms](#2-page-replacement-algorithms)
3. [Memory allocation](#3-memory-allocation)
4. [Priority CPU scheduling](#4-priority-cpu-scheduling)
5. [Process synchronization](#5-process-synchronization)
6. [Test entry points](#6-test-entry-points)
7. [Complexity summary](#7-complexity-summary)

## 1. Command-line application

### `os.App`

Source: [`src/main/java/os/App.java`](../src/main/java/os/App.java)

Runs sample scenarios for all modules using a single executable. This class does not contain allocation or scheduling logic: it delegates to the domain classes.

| Member | Parameters | Return / side effect | Notes |
| --- | --- | --- | --- |
| `main(String[] args)` | CLI tokens | `void`; exits nonzero on error | Entry point configured in Maven's JAR manifest. |
| `run(String[] args, PrintStream out, PrintStream err)` | Tokens and output streams | Status `0` success, `2` invalid input, `1` execution failure | Public for testable CLI behavior; accepts `help`, `all`, and each module name. |
| `requireArgs(String[] args, int count)` *(private)* | Arguments and expected count | Validates arity | Throws `IllegalArgumentException` for unexpected extra tokens. |
| `parsePages(String input)` *(private)* | Comma-separated integer IDs | `List<Integer>` | Empty string becomes empty trace; malformed entries cause an error; negative IDs are rejected by the simulators. |
| `runPaging(boolean fifo, int frames, List<Integer> pages, PrintStream out)` *(private)* | Policy, frame count, reference sequence, output | Prints per-step state, victim and counters | Uses FIFO if true, LRU otherwise. |
| `runAllocation(PrintStream out)` *(private)* | Output stream | Prints First/Best/Worst Fit results | Resets partition state for each strategy. |
| `runMft(PrintStream out)` *(private)* | Output stream | Prints fixed-partition example | Reports internal fragmentation. |
| `runMvt(PrintStream out)` *(private)* | Output stream | Prints variable-partition example | Reports contiguous free memory. |
| `runScheduling(PrintStream out)` *(private)* | Output stream | Prints process order and wait/turnaround times | Uses fixed sample processes. |
| `runProducerConsumer(PrintStream out)` *(private)* | Output stream | Prints consumed sequence | May propagate interruption. |
| `runDining(PrintStream out)` *(private)* | Output stream | Prints meal counts | May propagate interruption. |
| `usage(PrintStream out)` *(private)* | Output stream | Prints help text | Documents commands and paging arguments. |

`os.App()` is private: the class contains only static methods. Only FIFO/LRU subcommands accept additional page and frame arguments.

## 2. Page-replacement algorithms

### `os.paging.FifoPageReplacement`

Source: [`src/main/java/os/paging/FifoPageReplacement.java`](../src/main/java/os/paging/FifoPageReplacement.java)

FIFO chooses the oldest **loaded** page on a fault. A hit never changes its insertion/replacement order. The algorithm maintains a circular index to the next free-or-replace frame.

| Member | Parameters | Return / behavior | Errors / complexity |
| --- | --- | --- | --- |
| `FifoPageReplacement(int frameCount)` | Positive number of frames | Creates independent simulator | `IllegalArgumentException` for `frameCount <= 0`; `O(1)` |
| `getFrameCount()` | None | Configured capacity (`int`) | `O(1)` |
| `simulate(List<Integer> references)` | Non-null list of non-null, non-negative page IDs | `SimulationResult` with outcomes and immutable snapshots | `NullPointerException` for null values, `IllegalArgumentException` for negatives; `O(RF)` time and trace space |

For each of `R` references, `simulate` scans at most `F` frames, records whether the result is a hit, free-frame load or replacement, and copies the frame state. Repeated calls start with empty frames.

### `os.paging.LruPageReplacement`

Source: [`src/main/java/os/paging/LruPageReplacement.java`](../src/main/java/os/paging/LruPageReplacement.java)

LRU tracks the most recent access index for each occupied frame; **hits update recency**.

| Member | Parameters | Return / behavior | Errors / complexity |
| --- | --- | --- | --- |
| `LruPageReplacement(int frameCount)` | Positive number of frames | Creates simulator | `IllegalArgumentException` if not positive |
| `getFrameCount()` | None | Frame count (`int`) | `O(1)` |
| `simulate(List<Integer> pageReferences)` | Ordered references | Complete immutable `SimulationResult` | Rejects null/negative IDs; `O(RF)` time and trace space |
| `findPage(int[] frames, int pageId)` *(private)* | Frame array and page | Frame index or `-1` | `O(F)` |
| `findEmptyFrame(int[] frames)` *(private)* | Frame array | First empty index or `-1` | `O(F)`; uses internal sentinel `-1` |
| `findLeastRecentlyUsedFrame(long[] lastAccess)` *(private)* | Last-reference timestamps | Index of oldest frame | `O(F)`; called only when full |
| `snapshot(int[] frames)` *(private)* | Internal frame contents | List with `null` for empty frames | `O(F)` |

### `os.paging.PageAccess`

Source: [`src/main/java/os/paging/PageAccess.java`](../src/main/java/os/paging/PageAccess.java)

Immutable record of one processed reference. Internal constructor `PageAccess(int requestIndex, int pageId, Outcome outcome, int frameIndex, Integer evictedPage, List<Integer> frames)` copies the snapshot and rejects a null outcome.

| Member | Return / meaning |
| --- | --- |
| `getRequestIndex()` | Zero-based index in the reference sequence. |
| `getPageId()` | Requested page ID. |
| `getOutcome()` | `HIT`, `FAULT_LOAD` or `FAULT_REPLACE`. |
| `getFrameIndex()` | Frame where the requested page resides after this step. |
| `getEvictedPage()` | Victim page ID, or `null` if no eviction. |
| `getFrames()` | Immutable per-step list; `null` entries denote free frames. |
| `isHit()` | True only for `Outcome.HIT`. |
| `isFault()` | True for either fault outcome. |

All getters and predicates are `O(1)`, except that reading the entire returned list is naturally proportional to frame count. The nested `Outcome` enum distinguishes the three outcomes.

### `os.paging.SimulationResult`

Source: [`src/main/java/os/paging/SimulationResult.java`](../src/main/java/os/paging/SimulationResult.java)

Immutable aggregate report. Its package-private constructor `SimulationResult(int frameCount, int hitCount, int faultCount, List<PageAccess> accesses, List<Integer> finalFrames)` defensively copies both lists.

| Member | Return / meaning |
| --- | --- |
| `getFrameCount()` | Physical frame count. |
| `getRequestCount()` | Total number of processed references. |
| `getHitCount()` | Number of references to resident pages. |
| `getFaultCount()` | Number of references requiring a page load/replacement. |
| `getFaultRate()` | `faultCount / requestCount` as a fraction (0.0 if no references). |
| `getAccesses()` | Immutable chronological `List<PageAccess>`. |
| `getFinalFrames()` | Immutable final frame contents, possibly containing `null`. |

Each getter is `O(1)`; constructing the report copies lists in `O(R + F)` in addition to the snapshots already recorded.

### `os.paging.App` (retained standalone LRU interface)

Source: [`src/main/java/os/paging/App.java`](../src/main/java/os/paging/App.java)

Older LRU-specific CLI retained for backwards compatibility and its existing JUnit tests. It can be run with `java -cp target/classes os.paging.App --frames 3 --pages 1,2,3`.

| Member | Purpose |
| --- | --- |
| `main(String[] args)` | Launches the LRU-only CLI; exits nonzero on invalid input. |
| `run(String[] args, PrintStream out, PrintStream err)` *(package-private)* | Parses `--frames`, `--pages`, `--help`; returns exit code. |
| `parsePages(String input)` *(private)* | Validates comma-separated page IDs. |
| `printReport(SimulationResult report, PrintStream out)` *(private)* | Prints step table and aggregate metrics. |
| `formatFrames(List<Integer> frames)` *(private)* | Uses dashes to display free slots. |
| `printUsage(PrintStream out)` *(private)* | Prints standalone CLI help. |

## 3. Memory allocation

### `os.memory.FitStrategy`

Source: [`src/main/java/os/memory/FitStrategy.java`](../src/main/java/os/memory/FitStrategy.java)

Enum constants: `FIRST_FIT`, `BEST_FIT`, `WORST_FIT`. Choosing a strategy is distinct from running a simulation.

### `os.memory.PartitionAllocator`

Source: [`src/main/java/os/memory/PartitionAllocator.java`](../src/main/java/os/memory/PartitionAllocator.java)

Stateful allocator for unequal **fixed** partitions. Every partition holds at most one process. It intentionally does not split blocks or reassign residual capacity in occupied blocks.

| Member | Parameters | Return / behavior | Errors / complexity |
| --- | --- | --- | --- |
| `PartitionAllocator(int[] partitionSizes, FitStrategy strategy)` | Array of positive partition capacities and non-null strategy | Copies partition sizes; initially all free | Null arguments rejected; sizes must be positive; `O(P)` |
| `allocate(int processBytes)` | Positive process size | Index of assigned free partition, or `-1` if none fits | Rejects non-positive size; `O(P)` |
| `deallocate(int index)` | Partition index | True if occupied partition was freed, false if already empty | Invalid index throws `IndexOutOfBoundsException`; `O(1)` |
| `getAllocatedBytes(int index)` | Partition index | Occupant's bytes, or zero if free | Checks index; `O(1)` |
| `getInternalFragmentationBytes()` | None | Sum of unused capacity *inside occupied partitions* (`long`) | `O(P)` |
| `getFreePartitionSizes()` | None | Immutable list of capacities of free partitions | `O(P)` |
| `getPartitionCount()` | None | Number of partitions | `O(1)` |
| `checkIndex(int index)` *(private)* | Partition index | Validates index | Throws `IndexOutOfBoundsException` when invalid; `O(1)` |

**Tie rules:** Best/Worst Fit choose the smallest/largest eligible capacity; for equal capacities, lowest index wins. First Fit picks the earliest eligible index. A newly created allocator has independent allocation state.

### `os.memory.FixedPartitionSimulator`

Source: [`src/main/java/os/memory/FixedPartitionSimulator.java`](../src/main/java/os/memory/FixedPartitionSimulator.java)

MFT allocates at most one process per partition of equal capacity and does not deallocate processes.

| Member | Parameters | Return / behavior | Complexity |
| --- | --- | --- | --- |
| `simulate(int totalBytes, int blockBytes, List<Integer> processes)` | Non-negative total memory, strictly positive partition size, positive process sizes | Immutable `Result`; one decision per input process | `O(N)` time and `O(N)` output space |

The method rejects invalid sizes and null lists/elements. Number of partitions is `totalBytes / blockBytes`. Remaining bytes smaller than a full partition are accounted separately.

#### `FixedPartitionSimulator.Result`

| Member | Return / meaning |
| --- | --- |
| `getAllocationDecisions()` | Immutable `List<Boolean>` in request order. |
| `getAllocatedCount()` | Number of accepted processes. |
| `getPartitionCount()` | Initial number of uniform partitions. |
| `getInternalFragmentationBytes()` | Total unused capacity **within allocated** partitions (`long`). |
| `getUnpartitionedBytes()` | Total memory that could not form a whole partition (`int`). |

### `os.memory.VariablePartitionSimulator`

Source: [`src/main/java/os/memory/VariablePartitionSimulator.java`](../src/main/java/os/memory/VariablePartitionSimulator.java)

Sequential MVT example: accepted processes consume exactly their requested bytes from a single contiguous free tail. No releases, hole formation, or compaction are modeled.

| Member | Parameters | Return / behavior | Complexity |
| --- | --- | --- | --- |
| `simulate(int totalBytes, List<Integer> processes)` | Non-negative total memory and positive process sizes | Immutable `Result` with per-request decisions | `O(N)` time / `O(N)` output space |

Null lists/elements and invalid sizes are rejected. A request equal to remaining capacity **fits**; an oversized request is rejected and later requests still run.

#### `VariablePartitionSimulator.Result`

| Member | Return / meaning |
| --- | --- |
| `getAllocationDecisions()` | Immutable acceptance decisions in input order. |
| `getAllocatedBytes()` | Total allocated memory (`long`). |
| `getFreeBytes()` | Remaining single contiguous free tail (`long`); not external fragmentation. |

## 4. Priority CPU scheduling

### `os.scheduling.ProcessSpec`

Source: [`src/main/java/os/scheduling/ProcessSpec.java`](../src/main/java/os/scheduling/ProcessSpec.java)

Immutable description of a CPU-bound process available at simulation time zero.

| Member | Parameters | Return / behavior |
| --- | --- | --- |
| `ProcessSpec(String id, int burstTime, int priority)` | Nonblank ID, positive burst, arbitrary integer priority | Creates process; invalid ID or burst throws `IllegalArgumentException`. |
| `getId()` | None | String ID. |
| `getBurstTime()` | None | Required CPU time units. |
| `getPriority()` | None | Larger number means higher priority, including zero and negative values. |

### `os.scheduling.PriorityScheduler`

Source: [`src/main/java/os/scheduling/PriorityScheduler.java`](../src/main/java/os/scheduling/PriorityScheduler.java)

Implements **non-preemptive priority scheduling** for processes all arriving at time zero. Each call independently sorts a copy of the input. Java's stable sorting preserves input order on equal priorities. Process IDs must be unique.

| Member | Parameters | Return / behavior | Errors / complexity |
| --- | --- | --- | --- |
| `schedule(List<ProcessSpec> processes)` | Non-null list of non-null uniquely named processes | `Result` with ordered timeline and averages | Null/duplicate process check; `O(N log N)` time and `O(N)` space |

#### `PriorityScheduler.Entry`

| Member | Return / meaning |
| --- | --- |
| `getProcess()` | Process associated with this scheduled run. |
| `getStartTime()` | Time the process begins CPU execution (`long`). |
| `getCompletionTime()` | Time it finishes (`long`). |
| `getWaitingTime()` | Time waited before execution. Equal to start time because arrival is zero. |
| `getTurnaroundTime()` | Completion minus arrival time. Equal to completion time in this model. |

#### `PriorityScheduler.Result`

| Member | Return / meaning |
| --- | --- |
| `getEntries()` | Immutable actual execution order, as `List<Entry>`. |
| `getAverageWaitingTime()` | Average waiting time (`double`); 0 for no processes. |
| `getAverageTurnaroundTime()` | Average turnaround time (`double`); 0 for no processes. |

All Entry/Result getters are `O(1)`.

## 5. Process synchronization

### `os.synchronization.BoundedBuffer<T>`

Source: [`src/main/java/os/synchronization/BoundedBuffer.java`](../src/main/java/os/synchronization/BoundedBuffer.java)

A bounded circular FIFO queue guarded by **three semaphores**: `empty`, `full`, and binary `mutex`. Storage and indices are private. Fair semaphore policies help waiting order but do not provide an unconditional fairness guarantee for all thread scheduling.

| Member | Parameters | Return / behavior | Errors / complexity |
| --- | --- | --- | --- |
| `BoundedBuffer(int capacity)` | Strictly positive number of items | Allocates bounded storage | Rejects non-positive capacity; `O(C)` space |
| `put(T item)` | Non-null value | Blocks when full; writes at circular tail | `InterruptedException` if interrupted waiting; `NullPointerException` for null; `O(1)` active work |
| `take()` | None | Blocks when empty; removes oldest element | `InterruptedException` if interrupted waiting; `O(1)` active work |
| `getCapacity()` | None | Configured number of slots | `O(1)` |
| `size()` | None | Exact item count while holding mutex | May block for mutex; `O(1)` active work |

**Cancellation safety:** If a thread acquires a counting permit but is interrupted before obtaining the mutex, the `finally` block restores the counting permit. After a successful insertion or removal, the opposing counting semaphore is signaled. The implementation does not use spin loops.

### `os.synchronization.ProducerConsumerDemo`

Source: [`src/main/java/os/synchronization/ProducerConsumerDemo.java`](../src/main/java/os/synchronization/ProducerConsumerDemo.java)

A finite demonstration with one producer thread and one consumer thread.

| Member | Parameters | Return / behavior |
| --- | --- | --- |
| `run(List<String> input, int capacity)` *(static)* | Ordered, non-null string items and positive capacity | Immutable list of consumed items in FIFO order. Starts and joins both workers; propagates caller interruptions and wraps worker failures in `IllegalStateException`. |

The method has a 10-second join safeguard against stalled workers, followed by interruption and cleanup. Its active work is `O(N)` for `N` items; blocking time depends on scheduling and buffer capacity. `ProducerConsumerDemo()` is private (utility class).

### `os.synchronization.DiningPhilosophersSimulator`

Source: [`src/main/java/os/synchronization/DiningPhilosophersSimulator.java`](../src/main/java/os/synchronization/DiningPhilosophersSimulator.java)

One thread per philosopher. Fork IDs are globally ordered; each worker acquires the lower-numbered fork then the higher-numbered fork, preventing circular-wait deadlock.

| Member | Parameters | Return / behavior |
| --- | --- | --- |
| `simulate(int philosopherCount, int rounds)` | At least two philosophers; non-negative meals per philosopher | `int[]` of completed meal counts, ordered by philosopher ID; may throw `InterruptedException` or `IllegalStateException` if workers fail to finish. |

Each meal uses nested synchronized blocks on the two adjacent fork objects. A finite run uses `O(P)` thread/fork/count storage and `O(P × rounds)` meal critical sections overall. Scheduling and lock contention affect wall-clock runtime. Each worker is joined with a timeout; the finite exercise does not prove starvation freedom for an arbitrary unbounded run.

## 6. Test entry points

- [`src/test/java/os/RegressionSuite.java`](../src/test/java/os/RegressionSuite.java) — `main(String[] args)` runs the complete **dependency-free** regression suite; private helpers `execute`, `check`, `eq`, `near`, and `throwsException` implement the harness. Each named private test group checks one area of functionality, including randomized independent models and concurrency.
- [`src/test/java/os/OperatingSystemsIntegrationTest.java`](../src/test/java/os/OperatingSystemsIntegrationTest.java) — JUnit 5 test invoking the same regression suite during Maven verification.
- [`src/test/java/os/paging/LruPageReplacementTest.java`](../src/test/java/os/paging/LruPageReplacementTest.java) — retained LRU regression tests with independent access-ordered map reference model.
- [`src/test/java/os/paging/AppTest.java`](../src/test/java/os/paging/AppTest.java) — retained standalone LRU CLI validation and error-path tests.

## 7. Complexity summary

`R` = page requests, `F` = physical frames, `P` = fixed partitions, `N` = process requests, `C` = buffer capacity.

| Module | Typical time | Storage |
| --- | --- | --- |
| FIFO | `O(RF)` | `O(RF)` for trace snapshots; `O(F)` live state |
| LRU | `O(RF)` | `O(RF)` for trace snapshots; `O(F)` live state |
| Fit-based fixed-partition allocator | `O(P)` per allocation | `O(P)` |
| MFT | `O(N)` | `O(N)` for decisions |
| Sequential MVT | `O(N)` | `O(N)` for decisions |
| Non-preemptive priority scheduling | `O(N log N)` | `O(N)` |
| Semaphore bounded-buffer operation | `O(1)` active work, unbounded waiting | `O(C)` total buffer |
| Producer/consumer finite demo | `O(N)` active work | `O(N + C)` incl. returned history |
| Dining philosophers | `O(P × rounds)` meal critical sections, scheduling-dependent runtime | `O(P)` |

Complexities exclude input/output printing and arbitrary waiting caused by operating-system scheduling. Algorithms with concurrency cannot guarantee a fixed wall-clock completion time on every system.

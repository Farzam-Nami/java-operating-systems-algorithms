# Operating Systems Algorithms in Java

A collection of Java implementations and executable demonstrations of operating-systems concepts, consolidated from undergraduate coursework and subsequently refactored for maintainability, input validation, and repeatable tests.

The modules cover **page replacement**, **memory allocation**, **CPU scheduling**, and **process synchronization**. Each algorithm is implemented as an independent Java class; a shared command-line application runs the examples.

> **Scope:** These are educational simulations, not a kernel, memory allocator for a real process, or production scheduler.

## Contents and recommended study order

| Order | Topic                                     | Implementation                                        |
| ----- | ----------------------------------------- | ----------------------------------------------------- |
| 1     | FIFO page replacement                     | `os.paging.FifoPageReplacement`                       |
| 2     | LRU page replacement                      | `os.paging.LruPageReplacement`                        |
| 3     | Fixed-partition allocation policies       | `os.memory.PartitionAllocator` (First/Best/Worst Fit) |
| 4     | MFT (fixed equal-size partitions)         | `os.memory.FixedPartitionSimulator`                   |
| 5     | MVT (sequential variable-size partitions) | `os.memory.VariablePartitionSimulator`                |
| 6     | Non-preemptive priority scheduling        | `os.scheduling.PriorityScheduler`                     |
| 7     | Producer–consumer using semaphores        | `os.synchronization.BoundedBuffer`                    |
| 8     | Dining philosophers                       | `os.synchronization.DiningPhilosophersSimulator`      |

## Requirements

- **JDK 11 or newer** to compile and run with the included scripts.
- **Maven 3.8+** to use the Maven build and JUnit 5 tests (optional for the standalone regression suite).
- No external libraries are needed to run the application.

## Run

From the repository root, execute the script for your operating system.

### Linux or macOS

```bash
./scripts/run-demo.sh
./scripts/run-demo.sh help
./scripts/run-demo.sh fifo 3 1,2,3,1,4
./scripts/run-demo.sh lru 3 1,2,3,1,4
./scripts/run-demo.sh scheduling
```

### Windows PowerShell

```powershell
.\scripts\run-demo.ps1
.\scripts\run-demo.ps1 help
.\scripts\run-demo.ps1 fifo 3 1,2,3,1,4
.\scripts\run-demo.ps1 producer-consumer
```

The script compiles all source files into an ignored `out/` directory and launches `os.App`. Run without arguments or with `all` to execute every sample.

### Maven

```bash
mvn clean test
mvn package
java -jar target/operating-systems-algorithms-1.0.0.jar all
java -jar target/operating-systems-algorithms-1.0.0.jar mft
java -jar target/operating-systems-algorithms-1.0.0.jar fifo 4 2,2,1,4,5,2,5,6,5,7
```

Supported commands: `all`, `fifo`, `lru`, `allocation`, `mft`, `mvt`, `scheduling`, `producer-consumer`, `dining-philosophers`, and `help`.

Only FIFO and LRU accept additional command-line arguments (`frame-count`, `comma-separated-pages`). The other commands use documented sample data; call their public simulator APIs to experiment with custom inputs. The previous standalone LRU CLI is also retained as `os.paging.App`.

## Verify

Run the self-contained regression suite (JDK only):

```bash
./scripts/test.sh
```

Or in PowerShell:

```powershell
.\scripts\test.ps1
```

With Maven installed, `mvn verify` also executes JUnit 5 tests and the same regression suite. GitHub Actions runs Maven verification on Java 11 and Java 17.

The regression suite validates known traces, randomized reference models, edge cases, CLI errors, interruption handling, concurrent producer/consumer traffic, and finite dining-philosopher completion. See [test report](docs/TEST_REPORT.md) for the precise verification scope.

## Repository layout

```text
src/
  main/java/os/
    App.java
    paging/           FIFO, LRU and immutable trace reports
    memory/           allocation policies, MFT and MVT
    scheduling/       process model and non-preemptive priority scheduler
    synchronization/  semaphore buffer and dining philosophers
  test/java/os/       regression suite and JUnit integration
scripts/             build, demo and test helpers for Windows and Unix
docs/                API reference, algorithms, test report
.github/workflows/   CI build and verification
```

## Documentation

- [API and Implementation Reference](docs/API_REFERENCE.md) — all main classes, constructors, methods, parameters, return values, errors, and complexity.
- [Algorithm Notes](docs/ALGORITHMS.md) — policies, invariants, worked examples, and important terminology.
- [Test Report](docs/TEST_REPORT.md) — checks performed locally and what requires CI verification.

## Design notes and limitations

- **Page replacement:** Page IDs are non-negative; frame count must be positive. FIFO does not modify replacement order on a hit; LRU does. Both produce immutable per-reference snapshots.
- **First/Best/Worst Fit:** One process occupies one fixed partition; no partial reuse of its unused bytes. The unused space inside an occupied partition is **internal fragmentation**.
- **MFT:** Equal-sized partitions; unpartitioned remainder bytes are reported separately from internal fragmentation.
- **MVT:** Sequential contiguous allocation without deallocation or compaction. The remaining free tail is **not** mislabeled external fragmentation. Fragmented holes are outside this simplified model.
- **Priority scheduling:** Non-preemptive, all processes available at time zero, higher numeric priority first, stable arrival/input order for ties. No round robin, arrival-time handling, or preemption.
- **Concurrency:** Bounded-buffer operations use counting and binary semaphores and are interruptible. Dining philosophers use a global fork order to prevent deadlock in a finite simulation; starvation freedom is not claimed.
- Simulations use sample inputs. They do not manipulate real operating-system memory, page tables, or scheduling queues.


# Algorithm Notes

## Page replacement: FIFO vs. LRU

Both algorithms manage a fixed number of physical page frames. On a **hit**, the requested page is resident and no replacement occurs. On a **fault**, the system loads it into an empty frame or evicts a resident page.

- **FIFO** removes the page that was loaded earliest, regardless of subsequent hits. A circular replacement pointer advances only on faults, including fills of free slots.
- **LRU** removes the page whose most recent reference is oldest. Every hit updates that page's last-access timestamp.

The implementations scan resident frames on each request (time `O(R × F)` for `R` references and `F` frames). Each report records one snapshot of `F` frame positions per request, requiring `O(R × F)` output space.

## Fixed-partition allocation policies

A process requires a strictly positive number of bytes and occupies one otherwise-free partition:

- **First Fit:** first free partition that fits.
- **Best Fit:** free fitting partition with least capacity.
- **Worst Fit:** free fitting partition with greatest capacity.

Capacity ties favor the lowest partition index. A process that cannot fit stays unallocated (`-1`); it is **not** assigned to a used partition simply because unused bytes remain inside that partition. Internal fragmentation is the sum of `capacity - allocated process size` across occupied partitions. Each allocation scans up to `P` partitions (`O(P)` time).

## MFT: fixed equal-sized partitions

For `M` total bytes and partition size `B`, `floor(M/B)` partitions are available; `M mod B` bytes are outside those partitions. A process fits if it requires at most `B` bytes and an unused partition remains. The internal fragmentation of an accepted process equals `B - process size`.

The remaining `M mod B` bytes are separately labeled **unpartitioned remainder**, not internal fragmentation of an occupied partition and not automatically external fragmentation.

## MVT: sequential variable-sized allocation

Processes are considered in input order. An accepted process consumes exactly its size from a contiguous free tail. A rejected process does not stop later, smaller processes. There are no releases or holes; unused tail space is **free memory**, not external fragmentation. A full MVT allocator with deallocation, hole tracking, compaction, and coalescing is outside this exercise.

## Priority CPU scheduling

All processes arrive at time `t=0`. Each process runs to completion (non-preemptive), in descending numeric priority. Equal-priority processes retain their original input order. For every process:

- `waiting time = start time` (because arrival is zero)
- `turnaround time = completion time = start + burst`

The example uses `P1(5,1)`, `P2(3,3)`, `P3(2,2)`, and `P4(4,3)`, with notation `(burst, priority)`. Execution order is `P2, P4, P3, P1`, average waiting time `4.75`, average turnaround time `8.25`. Sorting `N` processes costs `O(N log N)` time.

## Producer–consumer with semaphores

The bounded buffer uses three semaphores:

- `empty`: initially the buffer capacity; counts remaining slots.
- `full`: initially zero; counts published elements.
- `mutex`: initially one; protects indices, storage, and item count.

`put` waits for an empty slot, acquires the mutex, inserts and advances the write index, releases the mutex, then signals `full`. `take` performs the reverse operation. If interrupted **between** the counting permit and mutex acquisition, the counting permit is restored. The demo avoids CPU-burning busy waits and handles thread termination.

`put` and `take` are `O(1)` excluding waiting time. The FIFO storage requires `O(C)` space for capacity `C`.

## Dining philosophers

Each philosopher needs adjacent forks to eat. The classic circular-wait deadlock can occur when all philosophers lock their left fork and then wait for their right fork. This implementation obtains the lower-numbered fork first, then the higher-numbered one. This **global acquisition order** breaks circular wait and prevents deadlock. Finite runs return counts of meals per philosopher.

Lock ordering does not by itself prove starvation freedom for an arbitrary unbounded scheduler, and simulated `synchronized` locks are not the same as actual operating-system process scheduling.

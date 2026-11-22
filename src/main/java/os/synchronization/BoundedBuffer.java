package os.synchronization;

import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * FIFO bounded buffer using counting semaphores and a binary semaphore.
 * Interrupted acquisition restores any previously obtained permit, so
 * cancellation does not reduce capacity or create phantom elements.
 *
 * @param <T> non-null element type
 */
public final class BoundedBuffer<T> {
    private final Object[] elements;
    private final Semaphore empty;
    private final Semaphore full;
    private final Semaphore mutex = new Semaphore(1, true);
    private int writeIndex;
    private int readIndex;
    private int count;

    /** Constructs a bounded buffer with positive capacity. */
    public BoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Buffer capacity must be positive.");
        }
        elements = new Object[capacity];
        empty = new Semaphore(capacity, true);
        full = new Semaphore(0, true);
    }

    /**
     * Inserts one item, blocking when the buffer is full.
     *
     * @throws InterruptedException if interrupted while waiting for capacity or the mutex
     */
    public void put(T item) throws InterruptedException {
        Objects.requireNonNull(item, "Buffer items must not be null.");
        empty.acquire();
        boolean acquiredMutex = false;
        boolean inserted = false;
        try {
            mutex.acquire();
            acquiredMutex = true;
            elements[writeIndex] = item;
            writeIndex = (writeIndex + 1) % elements.length;
            count++;
            inserted = true;
        } finally {
            if (acquiredMutex) {
                mutex.release();
            }
            if (inserted) {
                full.release();
            } else {
                empty.release();
            }
        }
    }

    /**
     * Removes and returns the oldest element, blocking while the buffer is empty.
     *
     * @throws InterruptedException if interrupted while waiting for an item or the mutex
     */
    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        full.acquire();
        boolean acquiredMutex = false;
        boolean removed = false;
        T result = null;
        try {
            mutex.acquire();
            acquiredMutex = true;
            result = (T) elements[readIndex];
            elements[readIndex] = null;
            readIndex = (readIndex + 1) % elements.length;
            count--;
            removed = true;
        } finally {
            if (acquiredMutex) {
                mutex.release();
            }
            if (removed) {
                empty.release();
            } else {
                full.release();
            }
        }
        return result;
    }

    /** Total number of slots. */
    public int getCapacity() { return elements.length; }

    /** Returns an exact snapshot of the item count protected by the mutex. */
    public int size() {
        mutex.acquireUninterruptibly();
        try {
            return count;
        } finally {
            mutex.release();
        }
    }
}

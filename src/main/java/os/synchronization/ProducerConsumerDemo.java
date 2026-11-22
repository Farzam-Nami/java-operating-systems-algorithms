package os.synchronization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Executes a finite single-producer, single-consumer bounded-buffer exercise. */
public final class ProducerConsumerDemo {
    private ProducerConsumerDemo() { }

    /**
     * Produces all items and returns their consumption order.
     * Worker failures and interrupts propagate instead of being swallowed.
     *
     * @param input non-null ordered items
     * @param capacity positive buffer capacity
     * @throws InterruptedException if the calling thread is interrupted
     */
    public static List<String> run(List<String> input, int capacity) throws InterruptedException {
        Objects.requireNonNull(input, "Input items must not be null.");
        for (String item : input) {
            Objects.requireNonNull(item, "Input item must not be null.");
        }
        BoundedBuffer<String> buffer = new BoundedBuffer<>(capacity);
        List<String> consumed = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                for (String item : input) {
                    if (failure.get() != null) { return; }
                    buffer.put(item);
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "producer");
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < input.size(); i++) {
                    if (failure.get() != null) { return; }
                    consumed.add(buffer.take());
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "consumer");
        producer.start();
        consumer.start();
        try {
            producer.join(10_000);
            consumer.join(10_000);
            if (producer.isAlive() || consumer.isAlive()) {
                throw new IllegalStateException("Producer/consumer simulation did not complete.");
            }
        } finally {
            producer.interrupt();
            consumer.interrupt();
            if (producer.isAlive()) { producer.join(1000); }
            if (consumer.isAlive()) { consumer.join(1000); }
        }
        if (failure.get() != null) {
            throw new IllegalStateException("Worker failed.", failure.get());
        }
        return Collections.unmodifiableList(new ArrayList<>(consumed));
    }
}

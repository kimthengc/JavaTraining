import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Lab_4_1_SharedState {

    /*
     * ========================================================================
     * Activity 1: The Lost Update
     * -------------------------------------------------------------------
     * Two threads each increment a shared counter 100_000 times.
     * Expected total: 200_000. Actual total (before fix): something less,
     * and different on every run. The race window inside counter++ is
     * wide enough that the OS scheduler corrupts the count reliably.
     * ======================================================================
     */

    private static final int INCREMENTS_PER_THREAD = 100_000;

    // Shared state for Activity 1. volatile guarantees visibility but NOT
    // atomicity — counter++ is still three operations (read, add, write)
    // and still races. The volatile modifier is here to defeat JIT
    // optimisations that would otherwise cache the field in a register
    // across the loop and make the race window too narrow to observe
    // on repeated runs in the same JVM session.
    private static volatile int ac1_counter = 0;

    // Shared lock object used by the fixed version in TODO 1.3.
    private static final Object AC1_LOCK = new Object();

    // TODO 1.1 — create a method called ac1_increment

    // TODO 1.3 — replace ac1_increment
    private static void ac1_increment() {
        for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
            synchronized (AC1_LOCK) {
                ac1_counter++;
            }
        }
    }

    private static void activity1() throws InterruptedException {
        System.out.println("\n=== Activity 1: The Lost Update ===");

        // TODO 1.2 — uncomment the body below
        ac1_counter = 0;

        Thread t1 = new Thread(Lab_4_1_SharedState::ac1_increment, "ac1-t1");
        Thread t2 = new Thread(Lab_4_1_SharedState::ac1_increment, "ac1-t2");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        int expected = 2 * INCREMENTS_PER_THREAD;
        System.out.println(" expected: " + expected);
        System.out.println(" actual: " + ac1_counter);
        System.out.println(" lost: " + (expected - ac1_counter));
    }

    /*
     * ========================================================================
     * Activity 2: The Invisible Write
     * -------------------------------------------------------------------
     * One thread spins on !ac2_stopRequested. Another thread sleeps, then
     * sets the flag to true. Without volatile, the JIT hoists the read
     * out of the tight loop and the reader never sees the write.
     * A 3-second watchdog rescues the reader so the student is not stuck.
     * ======================================================================
     */

    private static final long WATCHDOG_TIMEOUT_MS = 3000;

    // TODO 2.3 — add the volatile modifier to the field below when fixing
    private static volatile boolean ac2_stopRequested = false;

    // Iteration count the reader achieved before exiting — proves the
    // reader was actually running and not instantly short-circuited.
    private static long ac2_iterations = 0;

    // TODO 2.1 — create two Runnable fields: ac2_reader and ac2_writer
    private static final Runnable ac2_reader = () -> {
        long iterations = 0;
        while (!ac2_stopRequested) {
            iterations++;
        }
        ac2_iterations = iterations;
    };

    private static final Runnable ac2_writer = () -> {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        ac2_stopRequested = true;
        System.out.println("  [writer] flag set at t=500ms");
    };

    private static void activity2() throws InterruptedException {
        System.out.println("\n=== Activity 2: The Invisible Write ===");

        // TODO 2.2 — uncomment the body below
        ac2_stopRequested = false;
        ac2_iterations = 0;

        long start = System.currentTimeMillis();

        Thread reader = new Thread(ac2_reader, "ac2-reader");
        reader.setDaemon(true);
        Thread writer = new Thread(ac2_writer, "ac2-writer");

        reader.start();
        writer.start();

        writer.join();

        // Wait up to WATCHDOG_TIMEOUT_MS for the reader to exit on its own.
        reader.join(WATCHDOG_TIMEOUT_MS);

        long elapsed = System.currentTimeMillis() - start;
        if (reader.isAlive()) {
            System.out.println(
                    " reader STILL RUNNING after " + elapsed + "ms — JIT hoisted the read, writer's flag is invisible");
            System.out.println(" (reader is a daemon thread, it will die when the JVM exits)");
        } else {
            System.out.println(" reader exited after " + elapsed + "ms");
            System.out.println(" reader iterations: " + ac2_iterations);
        }
    }

    /*
     * ========================================================================
     * Activity 3: Three Ways to Be Thread-Safe
     * -------------------------------------------------------------------
     * Same workload (8 threads, 250_000 increments each) against three
     * Counter implementations: naive (broken), synchronized, atomic.
     * Prints correctness and elapsed time side by side.
     * ======================================================================
     */

    private static final int AC3_THREADS = 8;
    private static final int AC3_INCREMENTS_PER_THREAD = 250_000;

    interface Counter {
        void increment();

        int get();
    }

    // TODO 3.1 — fill in the three counter implementations below
    static final class NaiveCounter implements Counter {
        private int value = 0;

        public void increment() {
            value++;
        }

        public int get() {
            return value;
        }
    }

    static final class SynchronisedCounter implements Counter {
        private int value = 0;

        public synchronized void increment() {
            value++;
        }

        public synchronized int get() {
            return value;
        }
    }

    static final class AtomicCounter implements Counter {
        private final AtomicInteger value = new AtomicInteger(0);

        public void increment() {
            value.incrementAndGet();
        }

        public int get() {
            return value.get();
        }
    }

    // TODO 3.2 — create a method called ac3_benchmark
    private static long[] ac3_benchmark(Counter counter) throws InterruptedException {
        Thread[] threads = new Thread[AC3_THREADS];
        long start = System.nanoTime();

        for (int i = 0; i < AC3_THREADS; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < AC3_INCREMENTS_PER_THREAD; j++) {
                    counter.increment();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads)
            t.join();

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new long[] { counter.get(), elapsedMs };
    }

    private static void activity3() throws InterruptedException {
        System.out.println("\n=== Activity 3: Three Ways to Be Thread-Safe ===");

        // TODO 3.3 — uncomment the body below
        int expected = AC3_THREADS * AC3_INCREMENTS_PER_THREAD;
        System.out.println(" workload: " + AC3_THREADS + " threads x "
        + AC3_INCREMENTS_PER_THREAD + " increments = " + expected + " expected");
        System.out.println();
        System.out.printf(" %-22s %12s %10s %8s%n", "implementation", "final count",
        "elapsed", "correct");
        System.out.printf(" %-22s %12s %10s %8s%n", "--------------", "-----------",
        "-------", "-------");
        
        Counter[] counters = {
        new NaiveCounter(),
        new SynchronisedCounter(),
        new AtomicCounter()
        };
        String[] names = { "NaiveCounter", "SynchronisedCounter", "AtomicCounter" };
        
        for (int i = 0; i < counters.length; i++) {
        long[] result = ac3_benchmark(counters[i]);
        long finalCount = result[0];
        long elapsedMs = result[1];
        String correct = (finalCount == expected) ? "yes" : "NO";
        System.out.printf(" %-22s %12d %8d ms %8s%n",
        names[i], finalCount, elapsedMs, correct);
        }
    }

    /*
     * ========================================================================
     * Menu
     * ======================================================================
     */

    public static void main(String[] args) throws InterruptedException {
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n----------------------------------------");
                System.out.println(" Lab 4.1 — Shared State & Memory Visibility");
                System.out.println("----------------------------------------");
                System.out.println(" 1) The Lost Update");
                System.out.println(" 2) The Invisible Write");
                System.out.println(" 3) Three Ways to Be Thread-Safe");
                System.out.println(" q) Quit");
                System.out.print(" > ");

                String choice = in.hasNextLine() ? in.nextLine().trim() : "q";

                switch (choice) {
                    // TODO 1.2 — uncomment the case below when Activity 1 is implemented
                    case "1" -> activity1();

                    // TODO 2.2 — uncomment the case below when Activity 2 is implemented
                    case "2" -> activity2();

                    // TODO 3.3 — uncomment the case below when Activity 3 is implemented
                    case "3" -> activity3();

                    case "q", "Q" -> {
                        return;
                    }
                    default -> System.out.println("  unknown choice: " + choice);
                }
            }
        }
    }
}

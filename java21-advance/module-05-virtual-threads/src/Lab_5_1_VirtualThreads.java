import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

//import java.util.concurrent.Executors;
//import java.util.concurrent.locks.ReentrantLock;

/**
 * Carrier pool size note (Activity 3)
 * -----------------------------------
 * Activity 3 demonstrates carrier thread pinning. The JVM's default carrier
 * pool size equals Runtime.availableProcessors(). On machines with many
 * cores, a small pin demo may not visibly stall because free carriers absorb
 * the pinned VTs. To make the stall reliably visible, run this lab with:
 *
 * java -Djdk.virtualThreadScheduler.maxPoolSize=2 Lab_5_1_VirtualThreads
 *
 * This caps the carrier pool to 2, so pinning even 2 VTs will halt progress.
 * On a 1-2 core machine the flag is unnecessary but harmless.
 */
public class Lab_5_1_VirtualThreads {

    // ========================================================================
    // Activity 1 scaffolding — executor swap throughput comparison
    // ========================================================================

    private static final int AC1_TASK_COUNT = 1_000;
    private static final int AC1_TASK_SLEEP_MS = 100;
    private static final int AC1_FIXED_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /** One I/O-bound task: sleep AC1_TASK_SLEEP_MS, then return its id. */
    private static final class Ac1IoTask implements java.util.concurrent.Callable<Integer> {
        private final int id;

        Ac1IoTask(int id) {
            this.id = id;
        }

        @Override
        public Integer call() throws InterruptedException {
            Thread.sleep(AC1_TASK_SLEEP_MS);
            return id;
        }
    }

    /**
     * Submit AC1_TASK_COUNT tasks to the given executor, wait for all, return
     * elapsed ms.
     */
    private static long ac1_runWorkload(ExecutorService pool) throws Exception {
        List<Future<Integer>> futures = new ArrayList<>(AC1_TASK_COUNT);
        long start = System.nanoTime();
        for (int i = 0; i < AC1_TASK_COUNT; i++) {
            futures.add(pool.submit(new Ac1IoTask(i)));
        }
        for (Future<Integer> f : futures) {
            f.get();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // TODO 1.1 — create ac1_runFixedPool() and ac1_runVirtualThreads() helpers
    private static long ac1_runFixedPool() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(AC1_FIXED_POOL_SIZE)) {
            return ac1_runWorkload(pool);
        }
    }

    private static long ac1_runVirtualThreads() throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            return ac1_runWorkload(pool);
        }
    }

    // TODO 1.2 — uncomment activity1() below once the helpers are pasted

    private static void activity1() throws Exception {
        System.out.println("=== Activity 1: Executor swap — fixed pool vs virtual threads ===");
        System.out.println(" Workload: " + AC1_TASK_COUNT + " tasks, each sleeping "
                + AC1_TASK_SLEEP_MS + "ms");
        System.out.println(" Ideal parallel-infinite elapsed: " + AC1_TASK_SLEEP_MS +
                "ms");
        System.out.println();

        System.out.println(" [warmup] priming the JIT — first-run numbers lie");
        ac1_runFixedPool();
        ac1_runVirtualThreads();

        System.out.println();
        System.out.println(" [measured run]");
        long fixedMs = ac1_runFixedPool();
        long vtMs = ac1_runVirtualThreads();

        System.out.println();
        System.out.printf(" Fixed(%d) : %5d ms%n", AC1_FIXED_POOL_SIZE, fixedMs);
        System.out.printf(" VirtualThreads : %5d ms%n", vtMs);
        System.out.printf(" Ratio : %.1fx%n", (double) fixedMs / Math.max(1, vtMs));
    }

    // ========================================================================
    // Activity 2 scaffolding — mount / unmount lifecycle
    // ========================================================================

    private static final int AC2_TASK_COUNT = 5;
    private static final int AC2_SLEEP_MS = 200;

    /**
     * Thread.currentThread().toString() on a virtual thread looks like:
     * VirtualThread[#31]/runnable@ForkJoinPool-1-worker-7
     * ^^^ ^^^^^^^^
     * VT id (stable) carrier name (may change)
     *
     * The substring after the '@' is the carrier. Before an unmount/remount
     * cycle it's one worker, after it may be a different worker.
     */
    private static String ac2_describe(String label) {
        return "  [" + label + "] " + Thread.currentThread();
    }

    // TODO 2.1 — create the ac2_blockingTask Runnable
    private static final Runnable ac2_blockingTask = () -> {
        System.out.println(ac2_describe("before sleep"));
        try {
            Thread.sleep(AC2_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        System.out.println(ac2_describe("after sleep "));
    };

    // TODO 2.2 — uncomment activity2() below once the task is pasted

    private static void activity2() throws Exception {
        System.out.println("=== Activity 2: Mount / Unmount lifecycle ===");
        System.out.println(" Spawning " + AC2_TASK_COUNT + " virtual threads.");
        System.out.println(" Each prints its thread identity before and after a " +
                AC2_SLEEP_MS + "ms sleep.");
        System.out.println(" Watch the VT id (stable) and carrier name (may change) across the sleep.");
        System.out.println();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < AC2_TASK_COUNT; i++) {
                futures.add(pool.submit(ac2_blockingTask));
            }
            for (Future<?> f : futures)
                f.get();
        }
    }

    // ========================================================================
    // Activity 3 scaffolding — carrier thread pinning
    // ========================================================================

    private static final int AC3_TASK_COUNT = 8;
    private static final int AC3_WORK_MS = 500;

    /**
     * Each task creates its OWN monitor/lock. There is no contention between
     * VTs — every task could in principle run fully in parallel. The only
     * reason the synchronized version stalls is carrier pinning. If we used
     * a single shared monitor, both versions would serialise on the lock
     * itself, and the pinning effect would be hidden.
     *
     * Simulated "work under a lock" — sleeps to represent a blocking I/O call
     * performed while the critical section is held.
     */
    private static void ac3_doWorkUnderLock() throws InterruptedException {
        Thread.sleep(AC3_WORK_MS);
    }

    // TODO 3.1 — create ac3_pinningTask() (synchronized version, per-task monitor)
    // we not able to see the pinning because the latest java 21 version already fixed it
    private static Runnable ac3_pinningTask() {
        final Object myMonitor = new Object();
        return () -> {
            synchronized (myMonitor) {
                try {
                    ac3_doWorkUnderLock();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    // TODO 3.2 — create ac3_nonPinningTask() (ReentrantLock version, per-task lock)
    private static Runnable ac3_nonPinningTask() {
        final ReentrantLock myLock = new ReentrantLock();
        return () -> {
            myLock.lock();
            try {
                ac3_doWorkUnderLock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                myLock.unlock();
            }
        };
    }

    // TODO 3.3 — uncomment activity3() below once both task factories are pasted

    private static void activity3() throws Exception {
        System.out.println("=== Activity 3: Carrier thread pinning ===");
        System.out.println(" " + AC3_TASK_COUNT + " virtual threads, each doing " +
                AC3_WORK_MS + "ms of work under a lock.");
        System.out.println(" Every task uses its own private monitor/lock — no contention between tasks.");
        System.out.println(" Run with -Djdk.virtualThreadScheduler.parallelism=2");
        System.out.println(" and -Djdk.tracePinnedThreads=full to see pin events.");
        System.out.println();

        System.out.println(" [synchronized version — pins the carrier]");
        long pinnedMs = ac3_runAll(Lab_5_1_VirtualThreads::ac3_pinningTask);
        System.out.println(" elapsed: " + pinnedMs + " ms");
        System.out.println();

        System.out.println(" [ReentrantLock version — does not pin]");
        long unpinnedMs = ac3_runAll(Lab_5_1_VirtualThreads::ac3_nonPinningTask);
        System.out.println(" elapsed: " + unpinnedMs + " ms");
        System.out.println();

        System.out.printf(" Ratio: %.1fx%n", (double) pinnedMs / Math.max(1,
                unpinnedMs));
    }

    private static long ac3_runAll(java.util.function.Supplier<Runnable> taskFactory) throws Exception {
        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < AC3_TASK_COUNT; i++) {
                futures.add(pool.submit(taskFactory.get()));
            }
            for (Future<?> f : futures)
                f.get();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // ========================================================================
    // Menu-driven runner
    // ========================================================================

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println();
                System.out.println("=== Lab 5.1 — Virtual Threads in Practice ===");
                System.out.println("  1) Executor swap: fixed pool vs virtual threads");
                System.out.println("  2) Mount / Unmount lifecycle");
                System.out.println("  3) Carrier thread pinning");
                System.out.println("  q) quit");
                System.out.print("Select: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> activity1();
                    case "2" -> activity2();
                    case "3" -> activity3();
                    case "q", "Q" -> {
                        return;
                    }
                    default -> System.out.println("Unknown choice: " + choice);
                }
            }
        }
    }
}

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ThreadPoolExecutor;

public class Lab_4_2_Executors {

    /*
     * ========================================================================
     * Shared configuration
     * -------------------------------------------------------------------
     * Most sizing auto-scales with the host's core count so the lab
     * behaves consistently on laptops, containers, and larger boxes.
     * CORES_OVERRIDE is a trainer-only knob: 0 means "use the host",
     * any positive value forces a specific core count for demos.
     * ======================================================================
     */

    private static final int CORES_OVERRIDE = 0; // 0 = use host cores

    private static int cores() {
        return (CORES_OVERRIDE > 0)
                ? CORES_OVERRIDE
                : Runtime.getRuntime().availableProcessors();
    }

    /*
     * ========================================================================
     * Activity 1: From Raw Threads to a Pool
     * -------------------------------------------------------------------
     * Submit 12 tasks (300ms sleep each) to a fixed pool of 4 threads.
     * Tasks run in 3 waves of 4. Pool internals (active count, queue
     * size) are printed mid-flight to make the decoupling between
     * submission and execution visible. Future.get() is the blocking
     * join point and the only place exceptions surface.
     * ======================================================================
     */

    private static final int AC1_POOL_SIZE = 4;
    private static final int AC1_TASK_COUNT = 12;
    private static final long AC1_TASK_MS = 300;

    /**
     * Simulates a unit of work: sleep, then return a result tagged with
     * the thread that ran it.
     */
    private static String ac1_task(int id) {
        try {
            Thread.sleep(AC1_TASK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "task-" + id + " ran on " + Thread.currentThread().getName();
    }

    // TODO 1.1 — create a method called ac1_submitAll
    private static List<Future<String>> ac1_submitAll(ExecutorService pool) {
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= AC1_TASK_COUNT; i++) {
            final int id = i;
            futures.add(pool.submit(() -> ac1_task(id)));
        }
        return futures;
    }

    private static void activity1() throws InterruptedException, ExecutionException {
        System.out.println("\n=== Activity 1: From Raw Threads to a Pool ===");

        // TODO 1.2 — uncomment the body below
        System.out.println(" pool size: " + AC1_POOL_SIZE
                + ", tasks: " + AC1_TASK_COUNT
                + ", per-task sleep: " + AC1_TASK_MS + "ms");
        System.out.println(" expected: ~" + (AC1_TASK_COUNT / AC1_POOL_SIZE)
                + " waves, ~" + (AC1_TASK_COUNT / AC1_POOL_SIZE * AC1_TASK_MS) + "ms total");
        System.out.println();

        try (ExecutorService pool = Executors.newFixedThreadPool(AC1_POOL_SIZE)) {

            long start = System.nanoTime();
            List<Future<String>> futures = ac1_submitAll(pool);

            // Snapshot the pool internals immediately after submit returns.
            // submit() is non-blocking, so all 12 tasks are already queued here.
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) pool;
            System.out.println(" immediately after submit():");
            System.out.println(" active threads: " + tpe.getActiveCount()
                    + " / " + AC1_POOL_SIZE);
            System.out.println(" queued tasks: " + tpe.getQueue().size()
                    + " (submitted but not yet running)");
            System.out.println();

            // get() blocks until each task's result is ready. This is where
            // any exception thrown inside a task would surface, wrapped in
            // ExecutionException.
            System.out.println(" collecting results via Future.get():");
            for (Future<String> f : futures) {
                System.out.println(" " + f.get());
            }

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println();
            System.out.println(" elapsed: " + elapsedMs + "ms");
        }
        // try-with-resources calls close() here, which on Java 21
        // ExecutorService is equivalent to shutdown() + awaitTermination().
    }

    /*
     * ========================================================================
     * Activity 2: Pool Sizing, CPU-Bound vs I/O-Bound
     * -------------------------------------------------------------------
     * Same task count, same workload character per task, two pool sizes:
     * cores-sized vs oversubscribed (= taskCount). Run once with an I/O
     * simulation (sleep) and once with a CPU loop. I/O-bound benefits
     * massively from oversubscription because threads spend most of
     * their time parked. CPU-bound does not, because all threads
     * compete for the same cores.
     *
     * Formula: threads ≈ cores × (1 + wait/compute)
     * - Pure CPU (wait=0): threads ≈ cores
     * - 90% wait / 10% compute: threads ≈ cores × 10
     * ======================================================================
     */

    private static final int AC2_TASK_MULTIPLIER = 4;
    private static final long AC2_IO_TASK_MS = 200;
    private static final long AC2_CPU_ITERATIONS_PER_CORE = 2_500_000L;

    /** Simulates I/O: the thread sleeps, so the CPU is free to run others. */
    private static void ac2_ioTask() {
        try {
            Thread.sleep(AC2_IO_TASK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulates CPU work: a hot loop that resists JIT dead-code elimination
     * by consuming its result into a volatile sink.
     */
    private static volatile double AC2_SINK;

    private static void ac2_cpuTask(long iterations) {
        double acc = 0;
        for (long i = 1; i <= iterations; i++) {
            acc += Math.sqrt(i);
        }
        AC2_SINK = acc; // publish so JIT can't eliminate the loop
    }

    // TODO 2.1 — create a method called ac2_runWorkload
    private static long ac2_runWorkload(int poolSize, int taskCount, Runnable task)
            throws InterruptedException {
        try (ExecutorService pool = Executors.newFixedThreadPool(poolSize)) {
            long start = System.nanoTime();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(pool.submit(task));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                }
            }
            return (System.nanoTime() - start) / 1_000_000;
        }
    }

    private static void activity2() throws InterruptedException {
        System.out.println("\n=== Activity 2: Pool Sizing, CPU-Bound vs I/O-Bound ===");

        // TODO 2.2 — uncomment the body below
        int c = cores();
        int taskCount = AC2_TASK_MULTIPLIER * c;
        long cpuIter = AC2_CPU_ITERATIONS_PER_CORE;

        System.out.println(" cores detected: " + c);
        System.out.println(" tasks: " + taskCount + " (" + AC2_TASK_MULTIPLIER + "× cores)");
        System.out.println();
        System.out.printf(" %-14s %-18s %10s%n", "workload", "pool size", "elapsed");
        System.out.printf(" %-14s %-18s %10s%n", "--------", "---------", "-------");

        // I/O-bound row 1: cores-sized pool
        long io1 = ac2_runWorkload(c, taskCount, () -> ac2_ioTask());
        System.out.printf(" %-14s %-18s %8d ms%n",
                "I/O-bound", "cores (" + c + ")", io1);

        // I/O-bound row 2: oversubscribed pool
        long io2 = ac2_runWorkload(taskCount, taskCount, () -> ac2_ioTask());
        System.out.printf(" %-14s %-18s %8d ms%n",
                "I/O-bound", "oversubscribed (" + taskCount + ")", io2);

        System.out.println();

        // CPU-bound row 1: cores-sized pool
        long cpu1 = ac2_runWorkload(c, taskCount, () -> ac2_cpuTask(cpuIter));
        System.out.printf(" %-14s %-18s %8d ms%n",
                "CPU-bound", "cores (" + c + ")", cpu1);

        // CPU-bound row 2: oversubscribed pool
        long cpu2 = ac2_runWorkload(taskCount, taskCount, () -> ac2_cpuTask(cpuIter));
        System.out.printf(" %-14s %-18s %8d ms%n",
                "CPU-bound", "oversubscribed (" + taskCount + ")", cpu2);
    }

    /*
     * ========================================================================
     * Activity 3: ForkJoinPool & Work-Stealing on Real Split/Merge
     * -------------------------------------------------------------------
     * Sum a transformation of a large double[] three ways:
     * 1. Sequential loop
     * 2. ForkJoinPool with a good threshold (10_000)
     * 3. ForkJoinPool with a bad threshold (10) — oversplitting
     *
     * Per-element work is deliberately non-trivial so sequential does
     * not vectorise into a tight SIMD loop. That makes the comparison
     * fair: the ForkJoinPool has real work to steal.
     *
     * CORES_OVERRIDE limits the pool's parallelism here (unlike Act 2,
     * where the JVM decides thread scheduling over the whole machine).
     * ======================================================================
     */

    private static final int AC3_ELEMENTS_PER_CORE = 1_000_000;
    private static final int AC3_GOOD_THRESHOLD = 10_000;
    private static final int AC3_BAD_THRESHOLD = 10;

    /**
     * Lazy-initialised once per JVM session. First activity3() call
     * populates it and warms the JIT.
     */
    private static double[] ac3_data;

    /**
     * Per-element transformation. Non-trivial enough that the sequential
     * loop does not auto-vectorise into a trivial SIMD sum.
     */
    private static double ac3_transform(double v) {
        return Math.sqrt(v) + Math.log(v + 1) * Math.sin(v * 0.001);
    }

    private static double ac3_sequential(double[] data) {
        double sum = 0;
        for (double v : data) {
            sum += ac3_transform(v);
        }
        return sum;
    }

    /**
     * RecursiveTask that splits the range in half until it falls below
     * threshold, then computes the transformed sum sequentially.
     */
    static final class SumTask extends RecursiveTask<Double> {
        private final double[] data;
        private final int lo, hi, threshold;

        SumTask(double[] data, int lo, int hi, int threshold) {
            this.data = data;
            this.lo = lo;
            this.hi = hi;
            this.threshold = threshold;
        }

        @Override
        protected Double compute() {
            int len = hi - lo;
            if (len <= threshold) {
                double sum = 0;
                for (int i = lo; i < hi; i++) {
                    sum += ac3_transform(data[i]);
                }
                return sum;
            }
            int mid = lo + len / 2;
            SumTask left = new SumTask(data, lo, mid, threshold);
            SumTask right = new SumTask(data, mid, hi, threshold);
            left.fork();
            double rightResult = right.compute();
            double leftResult = left.join();
            return leftResult + rightResult;
        }
    }

    // TODO 3.1 — create a method called ac3_forkJoin
    private static long ac3_forkJoin(double[] data, int threshold, int parallelism)
            throws InterruptedException {
        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            long start = System.nanoTime();
            AC2_SINK = pool.invoke(new SumTask(data, 0, data.length, threshold));
            return (System.nanoTime() - start) / 1_000_000;
        }
    }

    /**
     * Prepare the array (lazy, once per session) and warm the JIT so the
     * first timed row is not paying compilation cost. 3 sequential
     * passes + 3 ForkJoinPool passes settle both code paths.
     */
    private static void ac3_prepare() throws InterruptedException {
        if (ac3_data != null)
            return;

        int size = AC3_ELEMENTS_PER_CORE * cores();
        System.out.println("  preparing array (" + size + " elements) and warming JIT...");

        ac3_data = new double[size];
        for (int i = 0; i < size; i++) {
            ac3_data[i] = i + 1;
        }

        // Warmup: 3 sequential + 3 fork-join passes, results discarded.
        for (int i = 0; i < 3; i++) {
            AC2_SINK = ac3_sequential(ac3_data);
        }
        try (ForkJoinPool warm = new ForkJoinPool(cores())) {
            for (int i = 0; i < 3; i++) {
                AC2_SINK = warm.invoke(new SumTask(ac3_data, 0, ac3_data.length, AC3_GOOD_THRESHOLD));
            }
        }
    }

    private static void activity3() throws InterruptedException {
        System.out.println("\n=== Activity 3: ForkJoinPool & Work-Stealing on Real Split/Merge ===");

        // TODO 3.2 — uncomment the body below
        ac3_prepare();

        int c = cores();
        System.out.println(" cores: " + c
                + ", array size: " + ac3_data.length
                + ", per-element work: sqrt + log*sin");
        System.out.println();
        System.out.printf(" %-30s %10s%n", "strategy", "elapsed");
        System.out.printf(" %-30s %10s%n", "--------", "-------");

        // Row 1: sequential baseline
        long start1 = System.nanoTime();
        double seqSum = ac3_sequential(ac3_data);
        long seqMs = (System.nanoTime() - start1) / 1_000_000;
        System.out.printf(" %-30s %8d ms%n", "sequential", seqMs);

        // Row 2: ForkJoinPool with good threshold
        long fjGoodMs = ac3_forkJoin(ac3_data, AC3_GOOD_THRESHOLD, c);
        System.out.printf(" %-30s %8d ms%n",
                "fork-join (threshold=" + AC3_GOOD_THRESHOLD + ")", fjGoodMs);

        // Row 3: ForkJoinPool with bad threshold
        long fjBadMs = ac3_forkJoin(ac3_data, AC3_BAD_THRESHOLD, c);
        System.out.printf(" %-30s %8d ms%n",
                "fork-join (threshold=" + AC3_BAD_THRESHOLD + ")", fjBadMs);

        System.out.println();
        System.out.printf(" sanity check: sum = %.2f (identical across strategies)%n", seqSum);
    }

    /*
     * ========================================================================
     * Activity 4: Producer-Consumer with BlockingQueue & Backpressure
     * -------------------------------------------------------------------
     * One producer pushes 50 items into a bounded ArrayBlockingQueue
     * of capacity 5. Two consumer threads take() and process them
     * (200ms sleep each). The producer is faster than the consumers
     * can drain, so put() blocks when the queue is full. That block
     * IS the backpressure — the bounded queue pushes back on a
     * faster producer until downstream has room.
     *
     * Consumers write results into a ConcurrentHashMap and append to
     * a CopyOnWriteArrayList. Both collections are thread-safe without
     * any external synchronisation. After everything drains:
     * map.size() == 50 and list.size() == 50 prove no drops, no
     * duplicates, no ConcurrentModificationException.
     * ======================================================================
     */

    private static final int AC4_QUEUE_CAPACITY = 5;
    private static final int AC4_ITEM_COUNT = 50;
    private static final int AC4_CONSUMER_COUNT = 2;
    private static final long AC4_CONSUMER_WORK_MS = 200;

    /**
     * Sentinel value placed on the queue to signal consumers to exit.
     * One sentinel per consumer so each one gets its own poison pill.
     */
    private static final Integer AC4_POISON_PILL = -1;

    /**
     * Producer body: push AC4_ITEM_COUNT items, then one poison pill per
     * consumer. Prints a timestamped line showing queue depth at each
     * put so backpressure is visible on the console.
     */
    private static void ac4_producer(BlockingQueue<Integer> queue, long startMs) {
        try {
            for (int i = 1; i <= AC4_ITEM_COUNT; i++) {
                queue.put(i); // blocks if queue is full
                long t = System.currentTimeMillis() - startMs;
                System.out.printf("    [t=%4dms] producer put #%2d, queue depth=%d%n",
                        t, i, queue.size());
            }
            // One poison pill per consumer, so each consumer wakes up and exits.
            for (int i = 0; i < AC4_CONSUMER_COUNT; i++) {
                queue.put(AC4_POISON_PILL);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Consumer body: take() in a loop, process, record results, exit on
     * poison pill.
     */
    private static void ac4_consumer(BlockingQueue<Integer> queue,
            ConcurrentHashMap<Integer, String> results,
            CopyOnWriteArrayList<String> order) {
        try {
            while (true) {
                Integer item = queue.take(); // blocks if queue is empty
                if (item.equals(AC4_POISON_PILL))
                    return;

                Thread.sleep(AC4_CONSUMER_WORK_MS); // simulate work
                String tag = "item-" + item + " by " + Thread.currentThread().getName();
                results.put(item, tag);
                order.add(tag);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // TODO 4.1 — create a method called ac4_runPipeline
    private static void ac4_runPipeline(BlockingQueue<Integer> queue,
            ConcurrentHashMap<Integer, String> results,
            CopyOnWriteArrayList<String> order,
            long start) throws InterruptedException {
        Thread producer = new Thread(() -> ac4_producer(queue, start), "producer");
        producer.start();

        try (ExecutorService consumers = Executors.newFixedThreadPool(AC4_CONSUMER_COUNT)) {
            for (int i = 0; i < AC4_CONSUMER_COUNT; i++) {
                consumers.submit(() -> ac4_consumer(queue, results, order));
            }
            producer.join();
            // try-with-resources close() waits for consumers to drain and exit.
        }
    }

    private static void activity4() throws InterruptedException {
        System.out.println("\n=== Activity 4: Producer-Consumer with BlockingQueue & Backpressure ===");

        // TODO 4.2 — uncomment the body below
        System.out.println(" queue capacity: " + AC4_QUEUE_CAPACITY
        + ", items: " + AC4_ITEM_COUNT
        + ", consumers: " + AC4_CONSUMER_COUNT
        + ", work/item: " + AC4_CONSUMER_WORK_MS + "ms");
        System.out.println(" producer has no delay; consumers are the bottleneck");
        System.out.println(" watch 'queue depth' — it climbs to " +
        AC4_QUEUE_CAPACITY + " and stays there");
        System.out.println();
        
        long start = System.currentTimeMillis();
        
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(AC4_QUEUE_CAPACITY);
        ConcurrentHashMap<Integer, String> results = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
        
        ac4_runPipeline(queue, results, order, start);
        
        long elapsed = System.currentTimeMillis() - start;
        System.out.println();
        System.out.println(" elapsed: " + elapsed + "ms");
        System.out.println(" results map size: " + results.size()
        + " (expected " + AC4_ITEM_COUNT + ")");
        System.out.println(" order list size: " + order.size()
        + " (expected " + AC4_ITEM_COUNT + ")");
        System.out.println(" no drops, no duplicates, no ConcurrentModificationException");
    }

    /*
     * ========================================================================
     * Menu
     * ======================================================================
     */

    public static void main(String[] args) throws Exception {
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n----------------------------------------");
                System.out.println(" Lab 4.2 — Executors, Pools & Concurrent Collections");
                System.out.println("----------------------------------------");
                System.out.println(" 1) From Raw Threads to a Pool");
                System.out.println(" 2) Pool Sizing, CPU-Bound vs I/O-Bound");
                System.out.println(" 3) ForkJoinPool & Work-Stealing");
                System.out.println(" 4) Producer-Consumer with BlockingQueue");
                System.out.println(" q) Quit");
                System.out.print(" > ");

                String choice = in.hasNextLine() ? in.nextLine().trim() : "q";

                switch (choice) {
                    // TODO 1.2 — uncomment the case below when Activity 1 is implemented
                    case "1" -> activity1();

                    // TODO 2.2 — uncomment the case below when Activity 2 is implemented
                    case "2" -> activity2();

                    // TODO 3.2 — uncomment the case below when Activity 3 is implemented
                    case "3" -> activity3();

                    // TODO 4.2 — uncomment the case below when Activity 4 is implemented
                    case "4" -> activity4();

                    case "q", "Q" -> {
                        return;
                    }
                    default -> System.out.println("  unknown choice: " + choice);
                }
            }
        }
    }
}

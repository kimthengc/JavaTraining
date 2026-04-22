# Lab 4.2 — Executors, Pools & Concurrent Collections

**Module:** 4 — Concurrency Fundamentals & Async Architecture
**Files:** `Lab_4_2_Executors.java`
**Compile:** `javac Lab_4_2_Executors.java`
**Run:** `java Lab_4_2_Executors`

---

## Activity 1: From Raw Threads to a Pool

*The Illusion* — Most developers think of a thread pool as "a thing that holds threads so I don't have to call `new Thread()` as often." That framing misses what the Executor framework actually gives you. 

A pool decouples *submission* from *execution*. You hand it work; it decides when, where, and on which thread that work runs. Your code is done the moment `submit()` returns. The result is a `Future`, a promise you cash in later. This activity makes that decoupling visible by printing the pool's internal state the instant after submission.

`Executors.newFixedThreadPool(4)` returns an `ExecutorService` backed by a `ThreadPoolExecutor` with exactly 4 worker threads and an unbounded `LinkedBlockingQueue`. When you `submit()` a task, one of two things happens: if a worker is idle, the task runs immediately; otherwise the task is placed on the queue and sits there until a worker becomes free. 

Either way, `submit()` returns instantly. It hands you a `Future<T>` that represents the eventual result. You call `.get()` on that `Future` when you actually need the value, and `.get()` blocks until the task has finished. 

This is also the only place task exceptions surface: if the task threw, `.get()` rethrows it wrapped in `ExecutionException`. Casting the pool to `ThreadPoolExecutor` exposes `getActiveCount()` and `getQueue().size()`, which make the submission/execution split tangible on the console. 

Java 21 also made `ExecutorService` `AutoCloseable`, so `try (ExecutorService pool = ...)` gives you a clean, orderly shutdown at the end of the block — no more `shutdown() + awaitTermination()` boilerplate.

### Steps

1. Open `Lab_4_2_Executors.java`.

2. Read the shared configuration block at the top. Note the `CORES_OVERRIDE` constant set to `0`, meaning "detect the host's cores at runtime." This is a knob used by Activities 2 and 3. 
    
    Activity 1 uses a fixed pool of 4 for clarity. Below that, read the Activity 1 scaffolding: `AC1_POOL_SIZE = 4`, `AC1_TASK_COUNT = 12`, `AC1_TASK_MS = 300`. Twelve tasks, each sleeping 300ms, into a pool of 4 — that is 3 waves of 4, roughly 900ms total.

3. Locate **TODO 1.1**.

    Create a method called `ac1_submitAll` that accepts the `ExecutorService` pool, submits `AC1_TASK_COUNT` tasks (each calling `ac1_task(id)` with a 1-based id), and returns the list of `Future<String>` handles. This is the "decoupled submission" half of the lesson — all 12 tasks get queued up in a tight loop before a single `.get()` is called.

4. Paste this snippet:

```java
private static List<Future<String>> ac1_submitAll(ExecutorService pool) {
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 1; i <= AC1_TASK_COUNT; i++) {
        final int id = i;
        futures.add(pool.submit(() -> ac1_task(id)));
    }
    return futures;
}
```

5. Locate **TODO 1.2**.

6. Uncomment the `activity1()` method body.

7. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

8. Compile and run. Select **1**.

9. Observe:
    - The "immediately after submit()" block reports `active threads: 4 / 4` and `queued tasks: 8`. All 12 tasks were handed to the pool in milliseconds. 4 are running, 8 are waiting their turn on the internal queue. `submit()` did not block — it returned the moment the task was accepted.
    - The result lines print in submission order because the loop calls `.get()` in submission order. Each `.get()` blocks until *that specific* task is done. You are watching the calling thread park, not the tasks execute.
    - The thread names repeat. `pool-1-thread-1` appears on multiple results because the same worker thread picked up another task after finishing its first. That is the "pool" working — threads are recycled, not created per task.
    - Total elapsed is roughly 3 × 300ms = 900ms. Three waves of four tasks each, executed in parallel within a wave.
    - **Question:** The try-with-resources block calls `close()` on the `ExecutorService` when it exits. What would happen if a task inside this pool threw an exception — would `close()` still complete cleanly, and where would that exception surface? Trace the path from the thrown exception to the place your code would actually see it.

---

## Activity 2: Pool Sizing, CPU-Bound vs I/O-Bound

*The Trap* — "I have 8 cores, so I'll use 8 threads" is a reasonable-sounding rule that is wrong about half the time. It is correct for CPU-bound work and dramatically wrong for I/O-bound work. 

The pain is that nothing in the API warns you. Both pool sizes compile, both run, both return correct answers. Only the stopwatch tells you that one of them is leaving most of the machine idle. 

This activity runs the same task count under both workload characters and both pool sizes, so the four outcomes sit side by side.

The formula the textbooks give is `threads ≈ cores × (1 + wait_time / compute_time)`. 

For pure CPU work, `wait_time` is zero, so the optimum is `cores` threads — one per core, no more. Any extra threads just take turns on the same cores while paying context-switching overhead. 

For I/O work, threads spend most of their life parked in a syscall waiting for the kernel to wake them up. While a thread is parked, its core is idle. Adding more threads means more work queued up to use those idle cores. With 200ms I/O and near-zero compute, you can oversubscribe by a factor of dozens and still win. 

The activity demonstrates both extremes: I/O-bound work with a cores-sized pool leaves most of the machine asleep (≈4× slowdown); CPU-bound work with an oversubscribed pool does not speed up (cores can't magically do more work in parallel than they have cores for). 

Module 5's Virtual Threads exist specifically to make the I/O-bound case effortless — you will write "one thread per request" code and the JVM schedules millions of them onto a small carrier pool. 

But Module 4 is where you learn *why* that matters by feeling the pain first.

### Steps

1. Read the Activity 2 scaffolding. Note `AC2_TASK_MULTIPLIER = 4` (so `taskCount = 4 × cores`), `AC2_IO_TASK_MS = 200` for the I/O simulation, and `AC2_CPU_ITERATIONS_PER_CORE = 2_500_000` for the CPU simulation. The CPU task uses `Math.sqrt` in a loop and writes its accumulator to a `volatile` sink so the JIT cannot eliminate the "dead" computation. Both task types have been defined for you as `ac2_ioTask()` and `ac2_cpuTask(iterations)`.

2. Locate **TODO 2.1**.

    Create a method called `ac2_runWorkload` that accepts a pool size, a task count, and a `Runnable` task, builds a fixed pool of that size, submits the task `taskCount` times, waits for them all to finish via `.get()`, and returns the elapsed time in milliseconds. This is the single benchmark primitive used for all four rows.

3. Paste this snippet:

```java
private static long ac2_runWorkload(int poolSize, int taskCount, Runnable task)
        throws InterruptedException {
    try (ExecutorService pool = Executors.newFixedThreadPool(poolSize)) {
        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            futures.add(pool.submit(task));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
```

4. Locate **TODO 2.2**.

5. Uncomment the `activity2()` method body.

6. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

7. Compile and run. Select **2**. Then select **2** again — the second run is the one to read. The first run pays JIT compilation cost on whichever row happens to execute first, which biases the table. The second run in the same JVM session has a settled JIT and produces the comparison the activity is actually about.

8. Observe the four rows of the table:
    - **I/O-bound, cores-sized pool:** slowest. Every thread spends most of its time sleeping, but there are only `cores` threads to sleep, so you process roughly `cores` items per 200ms window. With `4 × cores` tasks, that is 4 waves → ≈800ms.
    - **I/O-bound, oversubscribed pool:** fastest. All `4 × cores` tasks start simultaneously, all sleep simultaneously, all wake up together → ≈200ms. The machine was never busy; you just gave the kernel more parked threads to wake up.
    - **CPU-bound, cores-sized pool:** competitive. Exactly one thread per core, each core pinned to one task at a time, no contention overhead. This is what the formula tells you is optimal. On a 20-core box, expect ~15-20ms on the second run.
    - **CPU-bound, oversubscribed pool:** essentially identical elapsed time to the cores-sized run (within ~10-15%). More threads do not create more cores. The extra threads just time-slice onto the same hardware.
    - The CPU rows may vary a few percent run-to-run depending on thermals and OS activity. The point is the *ratio*: I/O gets a ≈4× speedup from oversubscription, CPU gets ≈1×.
    - **Question:** The formula says `threads ≈ cores × (1 + wait/compute)`. A web service where most requests make one 80ms database call and do 2ms of JSON serialisation sits at wait/compute = 40. On a 16-core box, that formula recommends ≈656 threads. Why does that number feel absurd on a traditional `ThreadPoolExecutor`, and what specifically about Java 21's Virtual Threads (Module 5) makes the same 656 "threads" ordinary?

---

## Activity 3: ForkJoinPool & Work-Stealing on Real Split/Merge

*The Crack* — Most demos of `ForkJoinPool` use recursive Fibonacci. Fibonacci teaches you the fork-compute-join mechanics, but it hides the most important tuning knob: the split threshold. Fibonacci's "merge" step is one addition, so it never exposes the cost of over-splitting. 

In real code, the split threshold is the single most important decision you make. Set it too coarse and you fail to saturate the pool. Set it too fine and the pool spends more cycles creating and merging tiny tasks than doing the actual work. 

This activity runs the same parallel sum with a good threshold and a deliberately bad one, so the cost curve is measurable instead of theoretical.

`ForkJoinPool` runs a work-stealing scheduler. Each worker thread has its own *deque* (double-ended queue). When a worker forks a subtask, that subtask goes onto the front of its own deque. When a worker runs out of work, it peeks at *other* workers' deques and steals from the back. This two-ended design is why stealing doesn't contend with the owner: the owner pushes and pops from one end, thieves take from the other. 

The consequence is that you don't need to manually balance work across threads. As long as you keep forking subtasks, idle workers will find them. The threshold is the knob that says "below this size, stop splitting and just do the work sequentially." 

A good threshold is large enough that the work done justifies the overhead of creating, forking, and joining the task object (typically thousands of elements for lightweight per-element work). 

A bad threshold (say 10) creates hundreds of thousands of tiny `RecursiveTask` objects for a million-element array, and each of those objects costs allocation, scheduling, and join-synchronisation. 

The bad configuration still parallelises and still beats sequential on large workloads but it squanders most of the potential speedup and can be three to ten times slower than the good configuration. The lesson is not "parallel is risky"; the lesson is "parallel has a cost curve and threshold is where you live on it."

### Steps

1. Read the Activity 3 scaffolding. The array is sized `AC3_ELEMENTS_PER_CORE × cores = 1_000_000 × cores` — enough work per core that parallelism has something to chew on. The per-element `ac3_transform` is `sqrt(v) + log(v+1) × sin(v × 0.001)`, deliberately non-trivial so the sequential baseline does not auto-vectorise into a trivial SIMD loop and make the comparison meaningless. Thresholds are `AC3_GOOD_THRESHOLD = 10_000` and `AC3_BAD_THRESHOLD = 10`. The `SumTask extends RecursiveTask<Double>` class and the `ac3_sequential` baseline are both pre-written — you only need to wire them up. Note also `ac3_prepare()`: it lazy-initialises the array on first call and runs three sequential + three ForkJoinPool warmup passes to settle the JIT before any timed row runs.

2. Locate **TODO 3.1**.

    Create a method called `ac3_forkJoin` that accepts the array, a threshold, and a parallelism level; builds a `ForkJoinPool` of that size; invokes a `SumTask` covering the whole array; and returns the elapsed time in milliseconds. Use try-with-resources — `ForkJoinPool` is `AutoCloseable` in Java 21.

3. Paste this snippet:

```java
private static long ac3_forkJoin(double[] data, int threshold, int parallelism)
        throws InterruptedException {
    try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
        long start = System.nanoTime();
        AC2_SINK = pool.invoke(new SumTask(data, 0, data.length, threshold));
        return (System.nanoTime() - start) / 1_000_000;
    }
}
```

4. Locate **TODO 3.2**.

5. Uncomment the `activity3()` method body.

6. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

7. Compile and run. Select **3**. The first run will pause for a moment at `preparing array and warming JIT...` while the array is populated and the warmup passes execute. Subsequent runs in the same JVM session are instant. **Read the first run's numbers** — the bad-threshold row shows its true cost when the fine-grained task path has not yet been JIT-compiled. Run it a few more times and watch the bad-threshold row shrink toward the good one, which is itself a second lesson (noted below).

8. Observe the three rows:
    - **Sequential:** the baseline. On a 20-core box this is around 150ms; on a 4-core laptop it is longer. This is the number parallelism has to beat.
    - **Fork-join with good threshold (10_000):** the headline. On any multi-core machine this is dramatically faster than sequential — a ≈10× speedup is typical on 16–20 cores. Work-stealing saturates the pool automatically; no balancing code was written.
    - **Fork-join with bad threshold (10):** *still faster than sequential*, but on the first run in a session the gap to the good threshold is large — typically 2-4× slower than the good row. That is the cost of creating, forking, and joining millions of tiny tasks instead of thousands. On subsequent runs, the JIT compiles the fine-grained task path and the gap narrows dramatically. This is itself a lesson: on a warm JVM with plenty of cores, work-stealing can almost paper over a bad threshold — but production code hits cold paths constantly (new request patterns, new data shapes, classloader boundaries), and you pay the "run 1" cost every time one is triggered.
    - The sanity check prints the sum. It is identical across all three strategies — parallelism is purely a performance concern, not a correctness one, as long as your fork/join logic is right.
     - **Question:** You ran Activity 3 multiple times and watched the bad-threshold row shrink from ~47ms on run 1 to ~17ms on run 3. What does that tell you about benchmark methodology in general, and what does it mean for a production service that frequently hits "cold" code paths — new endpoint versions, rare request shapes, classes loaded lazily under a feature flag? Where does the "warm benchmark" number lie to you?

---

## Activity 4: Producer-Consumer with BlockingQueue & Backpressure

*The False Promise* — New developers often ask "which queue should I use for producer-consumer?" and get pointed at `LinkedBlockingQueue`, which is technically unbounded. 

"Unbounded" sounds safe. It is the opposite. An unbounded queue with a faster producer than consumers will grow until it eats all available heap, at which point your service dies of `OutOfMemoryError` in production at 3am. 

A bounded queue, by contrast, does the right thing automatically: when it fills up, `put()` blocks. That block propagates backwards through the pipeline. The producer stops producing until downstream has room. This is *backpressure*, and it is the whole point of bounded queues.

`ArrayBlockingQueue(capacity)` is a fixed-size bounded queue. `put()` blocks when full. `take()` blocks when empty. Nothing about this is magic. Both methods park the calling thread on an internal `Condition` variable until the opposite operation signals them. The behaviour to watch for is this: the producer is unthrottled; the consumers are slow (200ms per item). 

The queue will fill to capacity within a few iterations and then stay at capacity for the duration of the run, because every time a consumer drains one item, the producer immediately pushes another. You will see this on the console as a sequence of `queue depth=5` lines; the producer is spending most of its wall-clock time parked inside `put()`. 

That parking is the backpressure doing its job. The results collections use `ConcurrentHashMap` and `CopyOnWriteArrayList` — a one-sentence callback to Lab 4.1: both are internally thread-safe, so two consumer threads can write concurrently without any `synchronized` on your part. 

After the pipeline drains, `map.size() == 50` and `list.size() == 50` prove there were no drops, no duplicates, and no `ConcurrentModificationException`.

### Steps

1. Read the Activity 4 scaffolding. Note `AC4_QUEUE_CAPACITY = 5` (deliberately tiny so you can see it saturate), `AC4_ITEM_COUNT = 50`, `AC4_CONSUMER_COUNT = 2`, and `AC4_CONSUMER_WORK_MS = 200`. Total consumer capacity is `2 consumers × (1 item / 200ms) = 10 items/sec`, so the 50 items will take roughly 5 seconds to drain. The `ac4_producer` and `ac4_consumer` methods are already written — note how the producer prints a timestamped line at each `put()` showing the queue depth, and how the consumer exits on a sentinel `AC4_POISON_PILL` value rather than polling a flag. The poison pill is the clean way to shut down consumers that are blocked in `take()`: one pill per consumer.

2. Locate **TODO 4.1**.

    Create a method called `ac4_runPipeline` that accepts the queue, the results map, the order list, and the start timestamp. It should start the producer on its own named thread, then use a two-consumer `ExecutorService` to run the consumer bodies, and wait for the producer to finish before returning. Use try-with-resources on the consumer `ExecutorService` — its `close()` will wait for the consumers to drain the queue and exit on their poison pills before returning.

3. Paste this snippet:

```java
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
```

4. Locate **TODO 4.2**.

5. Uncomment the `activity4()` method body.

6. Scroll down to `main()` and uncomment `case "4" -> activity4();`.

7. Compile and run. Select **4**.

8. Observe:
    - The first few `producer put` lines arrive in rapid succession — queue depth climbs 1, 2, 3, 4, 5. Then the pattern changes: the producer starts putting one item every ≈100ms (half the consumer work time, because there are two consumers). The queue stays pinned at depth 5 for the entire middle of the run. That is backpressure in action — the producer is parked inside `put()` waiting for a consumer to drain a slot.
    - Total elapsed is roughly 5 seconds: 50 items through a 10-item/sec pipeline.
    - `results.size() == 50` and `order.size() == 50`. Two threads wrote concurrently to both collections throughout the run, and neither collection corrupted. That is the job of `ConcurrentHashMap` and `CopyOnWriteArrayList` — thread-safety baked in, no external locks required.
    - The producer never saw an exception, never dropped an item, never got ahead of the consumers by more than 5 items. The bounded queue enforced that ceiling, automatically.
    - **Question:** If you replaced `new ArrayBlockingQueue<>(5)` with `new LinkedBlockingQueue<>()` (unbounded), the code would still compile, still run, still produce the correct 50-item result under this small workload. What specific failure mode does the unbounded queue expose that the bounded queue prevents, and at what scale would you first see it in production? Why doesn't the small demo reveal the problem?

---

## Lab Complete

A thread pool is not just a thread reuse optimisation — it is the boundary between submitting work and executing it, and every Executor pattern in Java builds on that split. Pool size is a tuning decision, not a default: CPU-bound work wants `cores`, I/O-bound work wants `cores × (1 + wait/compute)`, and getting this wrong costs you throughput silently. `ForkJoinPool` automates saturation through work-stealing but hands you the split threshold as the critical knob. Bounded queues give you backpressure for free; unbounded ones give you `OutOfMemoryError` for free. Module 5 will show you how Virtual Threads collapse most of the I/O-sizing problem into a non-question, but everything in this lab remains relevant for the CPU-bound and fork-join cases that Loom does not change.

# Lab 5.1 — Virtual Threads in Practice

**Module:** 5 — Virtual Threads & Project Loom
**Files:** `Lab_5_1_VirtualThreads.java`
Lab_5_1_VirtualThreads`

> **Run-flag callout**
>
> This lab must be run with two system properties set:
>
> - `-Djdk.virtualThreadScheduler.parallelism=2` caps the carrier pool to 2 threads. On a many-core development machine, the default carrier pool is too large to reliably show mount/unmount swaps in Activity 2 or carrier pinning stalls in Activity 3. Capping at 2 makes both effects visible regardless of host size.
> - `-Djdk.tracePinnedThreads=full` prints a stack trace the first time a pin event is detected. Harmless for Activities 1 and 2; essential evidence for Activity 3.
>
> If either flag is omitted, Activity 2 may look like nothing happened and Activity 3 may show two identical timings. Check the `java` command first if the output doesn't match the observation bullets.

---

## Activity 1: Executor Swap — Fixed Pool vs Virtual Threads

*The False Promise* — For two decades, the Java performance narrative around I/O-bound work has been "tune your thread pool." Pick a core-count multiplier, measure, retune. 

The advice isn't wrong for platform threads. Each one costs ~1MB of pre-allocated stack, so you have to be parsimonious. But the underlying premise, that thread count is the throughput ceiling, is a false promise for I/O-bound work. 

The ceiling isn't threads, it's stacks. Virtual threads remove the stack constraint, and the ceiling moves to where it should have always been: how much I/O the OS can actually service.

This activity runs 1000 I/O-bound tasks — each one sleeps 100ms, representing a typical remote call — against two executors. 

The first is `Executors.newFixedThreadPool(N)` where `N` is `Runtime.getRuntime().availableProcessors()` — a conventionally-tuned pool sized to the host's core count. The second is `Executors.newVirtualThreadPerTaskExecutor()`, which creates a virtual thread per submitted task with no pool sizing at all. 

Both run the same workload. The elapsed times differ by roughly 30-50× on a 20-core machine, and by an even larger factor on smaller hosts — the narrower the fixed pool, the more tasks have to wait their turn. 

The fixed pool's math is exact: 1000 tasks × 100ms sleep ÷ N concurrent workers =`(100000 / N)` ms of wall-clock time. On a 20-core host that's 5000ms; on an 8-core laptop it's 12500ms. 

The virtual-thread executor completes in ~100ms regardless of host core count, because all 1000 tasks run concurrently from the scheduler's perspective; the sleep is just 1000 parked continuations waiting on a timer. No tuning, no configuration, no `@param` annotations for pool size. 

### Steps

1. Open `Lab_5_1_VirtualThreads.java`.

2. Read the scaffolding at the top of the file. Note the **run-flag callout** in the class Javadoc — this lab is designed to be run with `-Djdk.virtualThreadScheduler.parallelism=2 -Djdk.tracePinnedThreads=full`. These flags are required for Activities 2 and 3 to produce visible evidence and are harmless for Activity 1.

3. Read the Activity 1 scaffolding. Note the constants `AC1_TASK_COUNT = 1000`, `AC1_TASK_SLEEP_MS = 100`, `AC1_FIXED_POOL_SIZE = Runtime.getRuntime().availableProcessors()`. The `Ac1IoTask` record simulates an I/O call. The `ac1_runWorkload(ExecutorService)` helper submits all tasks, waits for completion, returns elapsed ms. Every call to a real executor goes through this helper — the only thing that changes between the two runs is which executor you hand it.

4. Locate **TODO 1.1**.

    Create two helper methods. `ac1_runFixedPool()` opens a fixed-thread-pool executor of size `AC1_FIXED_POOL_SIZE` and runs the workload. `ac1_runVirtualThreads()` opens a virtual-thread-per-task executor and runs the same workload. Both use try-with-resources so the executor is closed and drained automatically. Both return the elapsed ms from the helper. These two are a single cohesive paste — they are meant to be read side by side as the sole difference between the two approaches.

5. Paste this snippet:

```java
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
```
    Also uncomment the import statement if it gives error on Executors

6. Locate **TODO 1.2**.

7. Uncomment the `activity1()` method.

8. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

9. Compile and run with the flags specified in the header. Select **1**.

10. Observe the measured run, not the warmup run. The warmup call is deliberate — first-run numbers are distorted by JIT compilation, and reading them would confuse the comparison. The measured run happens second, after the JIT has warmed both code paths.

11. Observe:
    - `Fixed(20)` takes approximately 5000ms. The math is exact: 1000 tasks × 100ms sleep / 20 concurrent workers = 5000ms. The pool is the bottleneck — task 21 cannot start until task 1 finishes.
    - `VirtualThreads` takes approximately 100-150ms. The math is different: 1000 tasks run "concurrently," each parks on a timer, the carrier threads handle the parking/unparking. Elapsed is dominated by the single 100ms sleep plus submission and unpark overhead.
    - The ratio on this workload is roughly 30-50×, depending on machine and JVM state. The absolute numbers vary; the ratio is the lesson.
    - **Question:** The fixed pool's 5000ms is not a JVM limitation — it's physics, given the pool size. Would increasing the fixed pool to 100 threads close the gap? What about 1000? At what point does "just make the pool bigger" stop being a workable strategy, and why?

---

## Activity 2: Mount / Unmount Lifecycle

*The Crack* — Activity 1 showed the throughput win but left the mechanism unexplained. The answer sits in one word from the Loom design: *continuation*. A virtual thread is a continuation. A pausable-resumable chunk of computation with plus a `Thread` API wrapper. 

When a VT calls a blocking operation like `Thread.sleep`, the runtime detaches the continuation from its carrier, parks it on whatever it was waiting for, and returns the carrier to the scheduler. The carrier is a real OS thread and can now run a different VT. When the original wait completes, the runtime schedules the VT onto any available carrier and not necessarily the one it came from. The VT identity is stable. The carrier underneath is not.

This activity makes the shift visible. Five virtual threads each print their thread identity before a sleep, sleep for 200ms, then print it again. 

The identity string has the form `VirtualThread[#N]/runnable@ForkJoinPool-1-worker-M`. The `#N` part is the VT id — stable for the life of the virtual thread. The `worker-M` part is the current carrier — it may have changed during the sleep. 

With the carrier pool capped at 2 (via the run flag) and 5 VTs competing for those 2 carriers, some VTs will land back on a different worker after their sleep. Not all — the scheduler is free to mount a VT on whatever carrier is available, including the one it started on. The non-determinism is the point. In a pooled-thread world, the thread is the work; the two are one object. In the Loom world, the work is the continuation, and the thread is just whatever OS resource happens to be running it right now.

### Steps

1. Read the Activity 2 scaffolding. Note the constants `AC2_TASK_COUNT = 5` and `AC2_SLEEP_MS = 200`. The `ac2_describe(String label)` helper prints the label and `Thread.currentThread()`. The Javadoc above it shows the identity string format and highlights which part is stable and which may change.

2. Locate **TODO 2.1**.

    Create a `Runnable` field called `ac2_blockingTask`. It should: print the current thread identity with label `"before sleep"`, call `Thread.sleep(AC2_SLEEP_MS)`, then print the current thread identity again with label `"after sleep "` (trailing space for column alignment). On interrupt, restore the interrupt flag and return.

3. Paste this snippet:

```java
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
```

4. Locate **TODO 2.2**.

5. Uncomment the `activity2()` method.

6. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

7. Compile and run with the flags specified in the header. Select **2**.
    `java -cp .\bin "-Djdk.virtualThreadScheduler.parallelism=2" "-Djdk.tracePinnedThreads=full" Lab_5_1_VirtualThreads`

8. Observe:
    - Each VT prints twice — once before its sleep, once after. The `VirtualThread[#N]` id is identical in both prints for the same VT. The VT survived the sleep intact.
    - The carrier name after the `@` is `ForkJoinPool-1-worker-1` or `ForkJoinPool-1-worker-2` — only two options, because `-Djdk.virtualThreadScheduler.parallelism=2` capped the pool.
    - **Some** VTs print `worker-1` before and `worker-2` after (or the reverse). That VT unmounted during sleep and re-mounted on a different carrier. Other VTs happen to land back on the same carrier — the scheduler is not required to change carrier, it's just free to.
    - Run Activity 2 two or three more times. The pattern of which VTs swap carriers changes every run. This is scheduler non-determinism; the specific pattern is irrelevant, the fact that it changes is the point.
    - **Question:** In a traditional `ThreadPoolExecutor`, the identity of the thread and the identity of the work are one and the same — whichever pool worker picks up your `Runnable` is the thread that runs it from start to finish. Virtual threads break that one-to-one relationship. What does this cost a developer who relied on it? Think about `ThreadLocal`, lock ownership, and anything else that was "anchored" to a platform-thread identity.

---

## Activity 3: Carrier Thread Pinning

*The Trap* — For most of the virtual threads era (JDK 21 GA through the early patch releases), the canonical Loom pitfall has been pinning. A VT that enters a `synchronized` block cannot unmount from its carrier until it exits the block, because the monitor ownership is tracked on the carrier's stack frame and that frame cannot be moved. If the VT then blocks — sleeps, waits on I/O, joins — the carrier is stuck too. Your lovely million-virtual-thread architecture silently degrades to a few-hundred-carrier-thread architecture the moment a hot path enters a `synchronized` block around a blocking call. The established advice for the past three years has been: migrate every such `synchronized` block to `ReentrantLock`, whose lock acquisition is continuation-aware and does not pin.

In late 2024, JEP 491 landed in JDK 24: the HotSpot VM was changed so that virtual threads can acquire, hold, and release monitors independently of their carriers. Pinning from `synchronized` is gone in JDK 24, and the fix has been progressively backported into Java 21 patch releases. That means the classic pinning demo you'll find in any 2023-era blog post may no longer reproduce on a current JDK. This activity runs that demo and tells you directly which world your JDK is in.

Eight tasks, each holding a lock for 500ms (simulating a critical section with blocking I/O inside), carrier pool capped at 2. Each task uses its own private monitor — no lock contention between tasks, so the only thing that can serialise them is carrier availability. One version uses `synchronized`, the other uses `ReentrantLock`. Depending on your JDK, you will see one of two outcomes, and both are correct — they just mean different things.

### Steps

1. Read the Activity 3 scaffolding. Note `AC3_TASK_COUNT = 8` and `AC3_WORK_MS = 500`. The `ac3_doWorkUnderLock()` helper is a stand-in for "blocking I/O performed inside a critical section." Read the Javadoc above it carefully — it explains why each task must create its own monitor/lock rather than sharing one.

2. Locate **TODO 3.1**.

    Create a method called `ac3_pinningTask()` that returns a `Runnable`. Inside the method, create a private `Object` to serve as the monitor. The returned `Runnable` enters a `synchronized` block on that monitor, calls `ac3_doWorkUnderLock()`, handles `InterruptedException` by restoring the interrupt flag. Because the method creates a new monitor on every call, each task will receive a distinct monitor — there is no contention between tasks.

3. Paste this snippet:

```java
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
```

4. Locate **TODO 3.2**.

    Create a method called `ac3_nonPinningTask()` that returns a `Runnable`. The structure mirrors `ac3_pinningTask()`: a private `ReentrantLock`, a returned `Runnable` that does `lock()`, tries the work, unlocks in `finally`. This is the conventional `ReentrantLock` idiom — the `finally` block guarantees the lock is released even if the work throws.

5. Paste this snippet:

```java
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
```

6. Locate **TODO 3.3**.

7. Uncomment the `activity3()` method and the `ac3_runAll()` helper.

8. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

9. First, check your JDK version. In the terminal run `java -version` and note the output. Older JDK 21 builds will still exhibit classic pinning; JDK 24 and recent JDK 21 patch releases will not.

10. Compile and run with the flags specified in the header. Select ***3***.
    `java -cp .\bin "-Djdk.virtualThreadScheduler.parallelism=2" "-Djdk.tracePinnedThreads=full" Lab_5_1_VirtualThreads`

11. Your output will match ONE of the two patterns below. Read the observation bullets for your pattern — and know why the other exists.

    **Pattern A — Classic pinning (older JDK 21 builds, pre-JEP-491 backport):**
    - Synchronized version: approximately 2000ms elapsed. A stack trace appears in stdout starting with `VirtualThread[#N] reason:MONITOR` — this is the pin trace fired by `-Djdk.tracePinnedThreads=full`. The trace names the lambda line inside `ac3_pinningTask()` with a `<== monitors:1` annotation on the owning frame.
    - ReentrantLock version: approximately 500ms elapsed. No pin trace.
    - Ratio: approximately **4×**.
    -Why: 8 tasks, carrier pool of 2, each carrier pinned for 500ms — only 2 tasks progress at a time, 8 ÷ 2 × 500ms = 2000ms. The ReentrantLock version unmounts properly during the sleep, all 8 tasks progress concurrently, wall-clock ~500ms.

    **Pattern B — JEP 491 applied (JDK 24, or recent JDK 21 patches):**
    - Both versions complete in approximately 500ms. Ratio ≈ 1×. No pin trace, even though `-Djdk.tracePinnedThreads=full` is set.
    - Why: JEP 491 changed the VM's monitor implementation so that virtual threads can unmount from inside a `synchronized` block. The `synchronized` version no longer pins its carrier during `Thread.sleep()`. Both primitives now behave identically for this workload.

12. Observe:
    - The `synchronized` vs `ReentrantLock` decision is returning to "use whichever best fits the code." The blanket "always migrate to `ReentrantLock`" rule from 2023 is no longer necessary on current JDKs.
    - Pinning is not completely gone even on JDK 24. Native method calls, Foreign Function & Memory API calls, and a handful of class-loading paths can still pin the carrier. These are much rarer in typical application code than the old `synchronized`-plus-I/O pattern.
    - `-Djdk.tracePinnedThreads=full` was the original diagnostic flag. On JDKs with JEP 491 applied, it has been removed (the flag is accepted silently but produces no output). The modern diagnostic is the JFR event `jdk.VirtualThreadPinned` — continuously recorded when JFR is active, filterable, and doesn't interfere with hot-path execution the way the trace flag did.
    - If you saw Pattern A: your JDK has the traditional Loom behaviour. This is the world most production systems are still in as of mid-2026. The `synchronized → ReentrantLock` refactor is a real win.
    - If you saw Pattern B: your JDK has the JEP 491 fix. Welcome to the post-pinning era. Spend your refactoring budget elsewhere.
    - **Question**: JEP 491 is a JVM-level fix that silently makes old code faster. You add a newer JDK to your CI, run your existing test suite, and your throughput goes up — no code change. When else in your career has a JDK upgrade done that for you? What does that tell you about where Java's scalability investments are landing, and about the value of keeping the runtime current even when your codebase doesn't evolve?

---

## Lab Complete

These three activities walk the Loom story end to end. Activity 1 shows *what* changes — a 30-50× throughput win for I/O-bound work, no tuning required. Activity 2 shows *why* it works — VTs unmount from their carriers during blocking operations, freeing the carriers to run other VTs, so one carrier can serve many parked continuations. Activity 3 shows *how it was breaks* — `synchronized` prevents unmounting, silently collapsing throughput back toward the carrier pool size — and shows that the JDK team has been quietly fixing this at the VM level. The mental model throughout: platform threads *are* the work; virtual threads *run* the work on whatever carrier happens to be free. The pitfalls are shrinking, not growing — the runtime is catching up to the abstraction.

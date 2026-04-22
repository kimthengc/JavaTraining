# Lab 4.3 — CompletableFuture & Async Composition

**Module:** 4 — Concurrency Fundamentals & Async Architecture
**Files:** `Lab_4_3_CompletableFuture.java`
**Compile:** `javac Lab_4_3_CompletableFuture.java`
**Run:** `java Lab_4_3_CompletableFuture`

---

## Activity 1: Future is a Wall, CompletableFuture is a Pipe

*The Illusion* —  Developers know `Future.get()` blocks. What they often don't viscerally feel is what that blocking *costs* — the main thread sits idle, doing nothing, while the work it's waiting on runs on another thread. 

Every `.get()` call is a thread-shaped hole in your throughput. `CompletableFuture` removes the wall: you describe the entire pipeline up front, hand it to the runtime, and the main thread is free to do other work until it actually needs the result. 

This activity runs the same two-stage pipeline (lookup user ID, then fetch profile) two ways — first with `Future.get()` blocking at every step, then with `CompletableFuture` chaining — and prints what the main thread was doing during each.

A second trap waits inside the CompletableFuture version. 

The first stage (`lookupUserIdAsync`) returns a `CompletableFuture<Long>`. 

The second stage (`fetchProfileAsync`) is *also* asynchronous — it returns a `CompletableFuture<Profile>`. 

If you chain them with `.thenApply(this::fetchProfileAsync)`, the type checker happily gives you a `CompletableFuture<CompletableFuture<Profile>>` — a nested future. The code compiles. It runs. Calling `.get()` on it returns a `CompletableFuture` object, not the `Profile` you wanted. 

This is exactly the same shape as the `Optional<Optional<T>>` and `Stream<Stream<T>>` problems — the fix is a flatMap-style operator. For `CompletableFuture`, that operator is `.thenCompose()`. It takes a function that itself returns a `CompletionStage` and flattens the result.

### Steps

1. Open `Lab_4_3_CompletableFuture.java`.

2. Read the scaffolding section at the top. Note the `User` and `Profile` records, the `STAGE_DURATION_MS` constant (300ms — long enough that the main thread can do observable work during the pipeline), and the two helper methods `lookupUserIdAsync(String username)` returning `CompletableFuture<Long>` and `fetchProfileAsync(Long userId)` returning `CompletableFuture<Profile>`. Both helpers sleep for `STAGE_DURATION_MS` before returning their result, simulating a slow network call. The blocking equivalents `lookupUserIdBlocking` and `fetchProfileBlocking` are also provided — same work, but they return the value directly instead of wrapping it in a future.

3. Locate **TODO 1.1**.

    Create a method called `ac1_runFuturePipeline` that calls `lookupUserIdBlocking` and `fetchProfileBlocking` in sequence and returns the resulting `Profile`. Print the main thread's name and the elapsed time at each stage so the blocking is visible.

4. Paste this snippet:

    ```java
    private static Profile ac1_runFuturePipeline(String username) {
        long start = System.nanoTime();
        System.out.println("  [Future] main thread: " + Thread.currentThread().getName());

        Long userId = lookupUserIdBlocking(username);
        System.out.println("  [Future] stage 1 done at t=" + ms(start) + "ms (main BLOCKED)");

        Profile profile = fetchProfileBlocking(userId);
        System.out.println("  [Future] stage 2 done at t=" + ms(start) + "ms (main BLOCKED)");

        return profile;
    }
    ```

5. Locate **TODO 1.2**.

    Create a method called `ac1_runBrokenCfPipeline` that chains the two async stages with `.thenApply()`. Return the resulting nested future as-is — do not call `.get()` on it inside this method. The caller will inspect the type and see the problem.

6. Paste this snippet:

    ```java
    private static CompletableFuture<CompletableFuture<Profile>> ac1_runBrokenCfPipeline(String username) {
        return lookupUserIdAsync(username)
                .thenApply(userId -> fetchProfileAsync(userId));
    }
    ```

7. Notice the return type. `CompletableFuture<CompletableFuture<Profile>>`. The compiler accepted it. The IDE may show a warning, but nothing stops this from compiling. This is the trap: the types match, the code is valid Java, and the runtime behaviour is wrong.

8. Locate **TODO 1.3**.

    Create a method called `ac1_runFixedCfPipeline` that does the same chain but uses `.thenCompose()` instead. The return type collapses from nested to flat.

9. Paste this snippet:

    ```java
    private static CompletableFuture<Profile> ac1_runFixedCfPipeline(String username) {
        return lookupUserIdAsync(username)
                .thenCompose(userId -> fetchProfileAsync(userId));
    }
    ```

10. Locate **TODO 1.4**.

11. Uncomment the `activity1()` method body.

12. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

13. Compile and run. Select **1**.

14. Observe the three sections of console output:
    - **Future pipeline**: main thread runs both stages directly. Elapsed time is roughly `2 × STAGE_DURATION_MS` (~600ms). The main thread was blocked the entire time — the "main BLOCKED" tags say so.
    - **Broken CF pipeline**: the type signature is CompletableFuture<CompletableFuture<Profile>>. Calling .join() on it returns the inner CompletableFuture instance, not a Profile. Notice the inner future prints as [Not completed] — thenApply resolved as soon as the lambda created the inner future, without waiting for it to finish. Total elapsed is ~300ms, not ~600ms, because the outer chain stopped halfway. To get the actual Profile you would need a second .join() on the inner future — which is exactly the nested-future ceremony thenCompose exists to eliminate. Notice also the main thread printed "doing other work" lines during the wait — it was free, even though the result is wrong.
    - **Fixed CF pipeline**: same chain, `.thenCompose()` instead of `.thenApply()`. Type signature is `CompletableFuture<Profile>`. `.join()` returns a real `Profile`. Main thread was free again, this time with the correct result.
    - The wall-clock time for both CF pipelines is roughly the same as the Future version (~600ms total) — `thenCompose` does not run things in parallel, it just chains them without blocking the submitting thread. The lesson is not "CF is faster end-to-end". The lesson is **"CF freed the main thread"**.
    - **Question:** If `thenApply` and `thenCompose` produce different types but the same execution sequence, when would you ever choose `thenApply` over `thenCompose`? What property of the transformation function decides?

---

## Activity 2: `allOf` Returns Void

*The Trap* — `CompletableFuture.allOf(cf1, cf2, cf3)` is the standard way to fan out parallel work and wait for everything to finish. Its name suggests it returns "all of the results." It does not. It returns `CompletableFuture<Void>` — a signal that says "they're all done now," with no payload. 

Developers who try to use the return value directly hit a wall: there is nothing to use. The correct pattern is to keep references to the original futures, wait on `allOf` for completion, and then call `.join()` on each original future to harvest its result. The futures have already completed by that point, so `.join()` returns immediately — no extra blocking.

The parallelism itself comes from where the work runs. `CompletableFuture.supplyAsync(...)` submits the task to `ForkJoinPool.commonPool()` by default. Three `supplyAsync` calls in a row submit three tasks to the pool, which the pool runs concurrently on its worker threads. 

Total wall-clock time is roughly the *maximum* of the three task durations, not the sum. On a host with at least 3 cores in the common pool, three tasks of varying durations finish in roughly the time of the longest one, not the sum of all three.

### Steps

1. Read the Activity 2 scaffolding. Note the three task durations: `AC2_FAST_MS = 200`, `AC2_MEDIUM_MS = 400`, `AC2_SLOW_MS = 300` — deliberately out of order so it's obvious which one wins the wall-clock race. There's also a helper `slowSupplier(String label, int durationMs)` that returns a `Supplier<String>` which sleeps for `durationMs` and then returns the label.

2. Locate **TODO 2.1**.

    Create a method called `ac2_runSequential` that runs the three suppliers one after another using `.get()` on each `CompletableFuture` before submitting the next. Return the elapsed time in milliseconds. This is the baseline — sequential execution, total time is the sum of all three.

3. Paste this snippet:

    ```java
    private static long ac2_runSequential() throws Exception {
        long start = System.nanoTime();
        String r1 = CompletableFuture.supplyAsync(slowSupplier("fast",   AC2_FAST_MS)).get();
        String r2 = CompletableFuture.supplyAsync(slowSupplier("medium", AC2_MEDIUM_MS)).get();
        String r3 = CompletableFuture.supplyAsync(slowSupplier("slow",   AC2_SLOW_MS)).get();
        System.out.println("  [seq] results: " + r1 + ", " + r2 + ", " + r3);
        return ms(start);
    }
    ```

4. Locate **TODO 2.2**.

    Create a method called `ac2_runParallelBroken` that submits all three tasks at once, waits with `allOf`, and tries to extract results from `allOf`'s return value. The `Void` return type makes this impossible — there is nothing to read. Print what `allOf` actually returned to make the lesson concrete.

5. Paste this snippet:

    ```java
    private static long ac2_runParallelBroken() throws Exception {
        long start = System.nanoTime();
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(slowSupplier("fast",   AC2_FAST_MS));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(slowSupplier("medium", AC2_MEDIUM_MS));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(slowSupplier("slow",   AC2_SLOW_MS));

        CompletableFuture<Void> all = CompletableFuture.allOf(cf1, cf2, cf3);
        Void nothing = all.get();
        System.out.println("  [parallel-broken] allOf returned: " + nothing + " (literally null, no results inside)");
        return ms(start);
    }
    ```

6. Locate **TODO 2.3**.

    Create a method called `ac2_runParallelCorrect` that uses the same fan-out, waits with `allOf`, then harvests each result by calling `.join()` on the original futures.

7. Paste this snippet:

    ```java
    private static long ac2_runParallelCorrect() throws Exception {
        long start = System.nanoTime();
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(slowSupplier("fast",   AC2_FAST_MS));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(slowSupplier("medium", AC2_MEDIUM_MS));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(slowSupplier("slow",   AC2_SLOW_MS));

        CompletableFuture.allOf(cf1, cf2, cf3).join();
        String r1 = cf1.join();
        String r2 = cf2.join();
        String r3 = cf3.join();
        System.out.println("  [parallel] results: " + r1 + ", " + r2 + ", " + r3);
        return ms(start);
    }
    ```

8. Locate **TODO 2.4**.

9. Uncomment the `activity2()` method body.

10. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

11. Compile and run. Select **2**. Run it **twice** — the first run includes JIT warmup and overstates the timings. Read the second run.

12. Observe the printed comparison:
    - **Sequential**: ~900ms wall-clock (200 + 400 + 300, plus a few ms of overhead). Three tasks, three waits, summed.
    - **Parallel-broken**: ~400ms wall-clock — the parallelism *worked*, but `allOf` returned `null`. The results never made it out. The fan-out was correct; the harvest was missing.
    - **Parallel-correct**: ~400ms wall-clock and all three results captured. Same fan-out, plus the `.join()`-each-original pattern.
    - On a 20-core host, expect parallel ≈ 400-450ms and sequential ≈ 900-950ms. The ratio is the lesson, not the absolute number — your machine will produce different absolute values, but parallel should always beat sequential by roughly 2×.
    - **Question:** `allOf` returns `Void` because `CompletableFuture` is a generic type and the futures passed in could each have a different result type — there's no single `T` that fits all of them. Given that constraint, why is the design choice `Void` rather than, say, `List<Object>` or `Object[]`? What would a `List<Object>` API force you to do at every call site?

---

## Activity 3: SLA Enforcement — `exceptionally` and `orTimeout`

*The Trap* — Async pipelines are forgiving in a dangerous way. If you don't terminate them with `.get()` or `.join()`, exceptions thrown inside the pipeline disappear into the runtime and never reach your error handler. Worse, a slow downstream service can keep your pipeline waiting forever. There is no implicit timeout on a `CompletableFuture`. 

Production code needs two things: a defined error path so failures don't vanish, and a defined deadline so slow calls don't hang the caller. 

`.exceptionally(fn)` gives you the error path. If any stage in the chain throws, `fn` is called with the exception and its return value becomes the pipeline's result. 

`.orTimeout(duration, unit)` gives you the deadline. If the pipeline hasn't completed within the timeout, it fails with `TimeoutException`. Combine them and you get "call this service, but if it takes too long or fails, return a sensible default."

`.orTimeout` does not cancel the underlying work. The slow task continues running on its worker thread; the timeout just fails the future you're waiting on, which then fires the `.exceptionally` fallback. The orphaned task eventually finishes and its result is discarded. 

This is fine for short tasks but worth knowing if your "slow service" holds an expensive resource. You'll still pay for that work, you just won't wait for it.

### Steps

1. Read the Activity 3 scaffolding. Note the constants: `AC3_TIMEOUT_MS = 200`, `AC3_FAST_TASK_MS = 50`, `AC3_SLOW_TASK_MS = 1000`. The fast task finishes well inside the timeout (4× headroom). The slow task takes 5× the timeout. There's a helper `callServiceAsync(int durationMs, String result)` that returns a `CompletableFuture<String>` which sleeps for `durationMs` and then returns `result`.

2. Locate **TODO 3.1**.

    Create a method called `ac3_callWithSla` that takes a `durationMs`. The pipeline calls `callServiceAsync`, attaches `.orTimeout()` for the deadline, and attaches `.exceptionally()` for the fallback that returns the string `"<fallback: " + cause + ">"`. The method returns the final result string.

3. Paste this snippet:

    ```java
    private static String ac3_callWithSla(int durationMs) {
        return callServiceAsync(durationMs, "real-result")
                .orTimeout(AC3_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> "<fallback: " + ex.getClass().getSimpleName() + ">")
                .join();
    }
    ```

4. Locate **TODO 3.2**.

5. Uncomment the `activity3()` method body.

6. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

7. Compile and run. Select **3**.

8. Observe:
    - **Happy path** (`AC3_FAST_TASK_MS = 50`, well under the 200ms SLA): pipeline returns `"real-result"` in ~50ms. The fallback never fired.
    - **Timeout path** (`AC3_SLOW_TASK_MS = 1000`, 5× over the 200ms SLA): pipeline returns `"<fallback: TimeoutException>"` in ~200ms. The slow task is still running on its worker thread when the SLA fires; the future fails fast; the fallback handler runs. The main thread waited only for the SLA, not for the actual work.
    - Neither path threw an exception out to the caller. Both completed cleanly. This is the goal: predictable latency and predictable error handling, regardless of what the downstream service does.
    - **Question:** `.orTimeout` fails the future but doesn't cancel the underlying task. In what real-world scenario would that orphaned work matter? What additional mechanism would you need if you actually had to *stop* the slow task?

---

## Lab Complete

Three activities, three async disciplines: chain without blocking (Activity 1), fan out without losing results (Activity 2), defend against slow and broken downstreams (Activity 3). The common thread is that `CompletableFuture` lets you describe the entire async workflow up front — the pipeline, the parallelism, the timeouts, the fallbacks — and hand it to the runtime in one shot. Every `Future.get()` you remove is a thread you give back. The cost is that async errors are silent unless you wire them up explicitly, and parallel composition is only useful if you remember to harvest the results yourself.

# Lab 2.3 — Pipeline Engineering: Performance, Refactoring & Exception Handling

**Module:** 2 — Functional Architecture & Stream Internals
**Files:** `Lab_2_3_PipelineEngineering.java`, `CheckedService.java`
**Compile:** `javac CheckedService.java Lab_2_3_PipelineEngineering.java`
**Run:** `java Lab_2_3_PipelineEngineering`

---

## Activity 1: The Boxing Tax

*The Hidden Cost* — At the call site, `Stream<Integer>` and `IntStream` look interchangeable. Both sum, both filter, both reduce. The code reads identically. But one of them allocates an `Integer` wrapper object for every element it touches, and the other works on raw `int` values directly.

On small datasets the difference is invisible. On production-scale data — the kind you hit when migrating legacy batch jobs — the boxing tax is measured in seconds and gigabytes of GC pressure.

Every element in `Stream<Integer>` is a heap-allocated `Integer` object: 16 bytes of header plus a 4-byte `int` field, accessed through a pointer dereference. `IntStream` stores primitive `int` values in a specialised pipeline that never touches the heap for numeric data.

The JIT can eliminate *some* boxing through escape analysis, but it cannot eliminate all of it especially when the stream produces objects that outlive the pipeline (e.g. `.collect()` into a `List<Integer>`). When you see `Stream<Integer>`, `Stream<Long>`, or `Stream<Double>` in code that does arithmetic, you're looking at an optimisation opportunity hiding in plain sight.

### Steps

1. Open `Lab_2_3_PipelineEngineering.java`.

2. Read the `N` constant at the top showing 10 million elements. Large enough to make timing differences obvious, small enough to run on any laptop in under a few seconds.

3. Locate **TODO 1.1**.

    Create a method called `ac1_boxedSumOfSquares` that sums the squares of integers 1..N using a boxed `Stream<Long>`. Return the result so it can be timed and printed.

4. Paste this snippet:

```java
    private static long ac1_boxedSumOfSquares() {
        return IntStream.rangeClosed(1, N).boxed()
                .map(i -> (long) i * i)
                .reduce(0L, Long::sum);
    }
```

5. Locate **TODO 1.2**.

    Create a method called `ac1_primitiveSumOfSquares` that performs the same computation using `IntStream`.

6. Paste this snippet:

```java
    private static long ac1_primitiveSumOfSquares() {
        return IntStream.rangeClosed(1, N)
                .mapToLong(i -> (long) i * i)
                .sum();
    }
```

7. Locate **TODO 1.3**.

8. Uncomment the `activity1()` method.

9. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

10. Compile and run `Lab_2_3_PipelineEngineering`.

11. Select **1** from the menu.

12. Observe:
    - Both pipelines produce the **same numerical answer** — the primitive version is not cheating on the maths.
    - The `IntStream` version is typically 3–10x faster. The exact ratio depends on JIT warm-up, hardware, and JVM version, but the primitive pipeline always wins.
    - The activity discards the first run of each pipeline and reports the second. This is deliberate — the JIT compiler needs a warm-up pass to optimise hot loops. Without warm-up discarding, the first-run numbers are noise.
    - The boxed pipeline uses `Stream<Long>` because `N = 10,000,000` produces a sum of squares that overflows `int`. This is itself a secondary lesson: primitive streams force you to be explicit about which primitive type you carry (`.mapToLong`), whereas `Stream<T>` silently autoboxes whatever wrapper type the lambda produces. That silence is sometimes a bug.
    - The `Stream<Long>` pipeline allocated roughly 10 million `Long` objects during its run. The `IntStream` pipeline allocated zero. That difference is the boxing tax.
    - **Question:** Both pipelines produce the same `long` answer, and primitive streams are faster for numeric work. So why does `Stream<Integer>` (and `Stream<Long>`, `Stream<Double>`) exist at all? What can you do with a boxed numeric stream that you fundamentally cannot do with `IntStream` / `LongStream` / `DoubleStream`?

---

## Activity 2: Method References — Beyond Syntax Sugar

*The Ambiguity* — `s -> s.toUpperCase()` and `String::toUpperCase` look like two spellings of the same thing. IDEs happily convert one to the other. Developers pick whichever they typed first and move on.

They are not the same thing. The lambda form generates a synthetic private method in your enclosing class, plus a `LambdaMetafactory` bootstrap to produce the functional interface instance. The method reference form targets the existing method directly — no synthetic wrapper, no extra frame in stack traces.

More importantly, a method reference communicates intent that a lambda cannot. When you write `String::toUpperCase`, you are promising the reader that exactly one method call happens, on the parameter, with no adaptation. The compiler enforces this promise — if the lambda body does anything more than that, it simply refuses to compile as a method reference.

That constraint is the feature. A method reference is a type-level guarantee that no hidden logic lurks inside the lambda. A lambda is a blank cheque.

### Steps

1. Locate **TODO 2.1**.

    Inside `activity2()`, Part A, create three equivalent pipelines that uppercase each string in `SAMPLE_INPUTS` — one using a lambda, one using an instance method reference, one using a static method reference that points at the `ac2_shout` helper.

2. Paste this snippet:

```java
        System.out.println("  --- Part A: three equivalent pipelines ---");

        List<String> viaLambda = SAMPLE_INPUTS.stream()
                .map(s -> s.toUpperCase())
                .collect(toList());
        System.out.println("  lambda                 -> " + viaLambda);

        List<String> viaInstanceRef = SAMPLE_INPUTS.stream()
                .map(String::toUpperCase)
                .collect(toList());
        System.out.println("  String::toUpperCase    -> " + viaInstanceRef);

        List<String> viaStaticRef = SAMPLE_INPUTS.stream()
                .map(Lab_2_3_PipelineEngineering::ac2_shout)
                .collect(toList());
        System.out.println("  Lab::ac2_shout         -> " + viaStaticRef);
```

3. Locate **TODO 2.2**.

    This step demonstrates the boundary of method references. Uncomment the block that tries to use a method reference for a lambda that does two chained calls. The compiler will reject it.

4. Uncomment the block marked *"compile-error demonstration"* inside `activity2()`.

5. Attempt to compile: `javac Lab_2_3_PipelineEngineering.java`.

6. Observe the compile error. The line `String::toUpperAndTrim` fails with *"cannot find symbol"* — no such method exists on `String`. And that is precisely the point: a method reference names exactly one existing method. There is no single JDK method that uppercases *and* trims. Therefore no method reference can describe both steps. The lambda body `s -> s.toUpperCase().trim()` chains two method calls; no `::` syntax can.

7. **Re-comment** the broken block so the rest of the lab compiles.

8. Locate **TODO 2.3**.

9. Uncomment `case "2" -> activity2();` in `main()`.

10. Compile and run. Select **2**.

11. Observe:
    - All three pipelines in Part A produce identical output. The method reference forms are not syntactic sugar — they target different underlying mechanisms (instance method on the argument vs. static method in the enclosing class), but the observable result is the same.
    - `String::toUpperCase` is an **unbound instance method reference** — the stream element becomes the receiver. `Lab_2_3_PipelineEngineering::ac2_shout` is a **static method reference** — the stream element becomes the sole argument. Both satisfy `Function<String, String>`.
    - The compile error in Part B is the boundary marker. If you can write a lambda as a method reference, the lambda was doing exactly one thing. If you cannot, the lambda was doing more — and that "more" was hidden inside what looked like one step.
    - **Question:** Why can `s -> s.toUpperCase()` be written as `String::toUpperCase`, but `s -> s.toUpperCase().trim()` cannot be written as a method reference? What does that restriction tell you about what a method reference actually *is* at the bytecode level?

---

## Activity 3: Checked Exceptions in Pipelines

*The Trap* — `Function<T, R>.apply` has no `throws` clause. Neither does `Consumer`, `Predicate`, `Supplier`, or any of the 40+ functional interfaces in `java.util.function`. The moment you try to call a checked-exception-throwing method inside a lambda — `Class.forName`, `Thread.sleep`, `URI` constructor, your own `parseCustomerFile(String path) throws IOException` — the compiler rejects your code.

Developers hit this, panic, and reach for one of three workarounds. Two of them scatter try/catch noise throughout every pipeline. The third is a one-line helper that isolates the ugliness and keeps the pipeline readable.

To build that helper, you need a functional interface that *permits* checked exceptions — something `java.util.function` deliberately doesn't give you. This is one of the legitimate reasons to reach for `@FunctionalInterface` and define your own single-abstract-method type: when the built-in functional interfaces don't fit your contract, a custom one is a single declaration away.

### Steps

1. Read `CheckedService.java`.

    Note two things:
    - `CheckedService.lookup(String id) throws IOException` — a method that deliberately throws for input `"BAD"` and succeeds for everything else. This simulates any real-world checked-throwing API (file I/O, network, parsing).
    - The `CheckedFunction<T, R>` interface at the bottom. It mirrors `Function<T, R>` but declares `throws Exception` on its single abstract method. The `@FunctionalInterface` annotation tells the compiler (and the next developer) that this type exists to be implemented as a lambda.

2. Locate **TODO 3.1**.

    The `CheckedService` declaration inside `activity3()` is commented out in the starter — before this activity, `Lab_2_3_PipelineEngineering.java` makes no reference to `CheckedService`, which keeps earlier activities compiling cleanly even if `CheckedService.java` hasn't been added to the classpath yet.

3. Uncomment the line:

```java
        CheckedService service = new CheckedService();
```

4. Locate **TODO 3.2**.

    Part A demonstrates the problem. Uncomment the broken direct call to see the compile error.

5. Uncomment the block marked *"broken direct call"* inside `activity3()`.

6. Attempt to compile: `javac CheckedService.java Lab_2_3_PipelineEngineering.java`.

7. Observe the compile error: *"unreported exception IOException; must be caught or declared to be thrown"*. The `Function<T, R>` contract has no `throws` clause — the lambda cannot propagate the checked exception even if the enclosing method declares it.

8. **Re-comment** the broken block.

9. Locate **TODO 3.3**.

    Part B: the inline try/catch wrapper. This works but clutters every pipeline that calls the service.

10. Paste this snippet:

```java
        System.out.println("  --- Part B: inline try/catch wrapper ---");

        List<String> inlineResults = INPUTS.stream()
                .map(id -> {
                    try {
                        return service.lookup(id);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(toList());

        System.out.println("  results: " + inlineResults);
```

11. Locate **TODO 3.4**.

    Part C: the `unchecked()` helper. Create a static method that takes a `CheckedFunction<T, R>` and returns a plain `Function<T, R>` that wraps any thrown checked exception in a `RuntimeException`.

12. Paste this snippet:

```java
    private static <T, R> Function<T, R> unchecked(CheckedFunction<T, R> fn) {
        return t -> {
            try {
                return fn.apply(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
```

13. Locate **TODO 3.5**.

    Use the helper in the pipeline.

14. Paste this snippet:

```java
        System.out.println("  --- Part C: unchecked() helper ---");

        List<String> helperResults = INPUTS.stream()
                .map(unchecked(service::lookup))
                .collect(toList());

        System.out.println("  results: " + helperResults);
```

15. Locate **TODO 3.6**.

16. Uncomment `case "3" -> activity3();` in `main()`.

17. Compile and run. Select **3**.

18. Observe:
    - Part A's compile error is the starting point — Java's type system genuinely forbids checked exceptions in built-in functional interfaces. There is no compiler flag that relaxes this.
    - Part B works but the pipeline now carries seven lines of try/catch plumbing for what used to be a one-line `.map()`. Multiply that across every pipeline in a real codebase and the functional style loses its readability advantage entirely.
    - Part C collapses the noise to `.map(unchecked(service::lookup))` — the pipeline reads as intent again. The try/catch still exists, but it lives *once* in the helper, not *N times* across the codebase.
    - The `CheckedFunction<T, R>` interface was six lines of code. That's the entire cost of defining your own `@FunctionalInterface` when the built-in ones don't fit. The payoff is every future pipeline that calls a throwing API.
    - **Question:** `Function<T, R>.apply` doesn't declare `throws Exception`. Why not? What would break in the rest of the Stream API — `.map()`, `.filter()`, `Collectors`, the whole ecosystem — if the core functional interfaces permitted checked exceptions?

---

## Activity 4: Parallel Stream Contention

*The Crack* — Lab 2.1 Activity 5 showed what happens when parallel streams execute a *broken* lambda: silent corruption from unsynchronised mutation. This activity shows what happens when parallel streams execute a *perfectly correct* lambda — but two of them run at the same time.

Every call to `.parallelStream()` in the entire JVM shares a single thread pool: `ForkJoinPool.commonPool()`. Your pipeline is never alone in it. Any library, framework, or piece of code anywhere in the JVM that uses the common pool — knowingly or not — competes for the same workers. When the pool is busy, your "parallel" stream waits in queue.

This matters in production. A web service that uses `.parallelStream()` under concurrent load doesn't get N×parallelism throughput — it gets parallelism throughput, shared across all requests. The per-request speedup disappears precisely when throughput matters most.

The fix is to submit the parallel stream into a *dedicated* `ForkJoinPool` that you own. Tasks submitted through `pool.submit(() -> stream.parallelStream()...)` execute on that pool's workers instead of the common pool. Two pipelines in two dedicated pools run truly in parallel — no contention, no queueing.

### Steps

1. Read the opening lines of `activity4()`. The first thing the activity prints is `ForkJoinPool.commonPool().getParallelism()` — the number of worker threads your JVM's default parallel-stream pool has. On a typical developer machine this is *cores - 1*. Inside a resource-limited container it may be lower.

2. Locate **TODO 4.1**.

    Part A: a single parallel pipeline with an artificial per-element delay. Measure wall time. This is the baseline — one pipeline with the whole common pool to itself.

3. Paste this snippet:

```java
        System.out.println("  --- Part A: one parallel pipeline alone ---");

        long startA = System.nanoTime();
        List<String> resultA = DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList());
        long elapsedA = (System.nanoTime() - startA) / 1_000_000;

        System.out.println("  items processed: " + resultA.size());
        System.out.println("  elapsed:         " + elapsedA + " ms");
```

4. Locate **TODO 4.2**.

    Part B: two parallel pipelines running concurrently in two threads, both submitting to the shared common pool. They will contend.

5. Paste this snippet:

```java
        System.out.println("Starving Common Pool ...");
        int parallelism = ForkJoinPool.getCommonPoolParallelism();
            for (int i = 0; i < parallelism; i++) {
                ForkJoinPool.commonPool().execute(() -> {
                    try { Thread.sleep(5_000); } catch (Exception e) {}
                });
            }
        System.out.println("Common Pool is now full and 'starved'...");

        System.out.println("  --- Part B: two parallel pipelines, shared common pool ---");

        long startB = System.nanoTime();
        Thread t1 = Thread.ofPlatform().start(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        Thread t2 = Thread.ofPlatform().start(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        t1.join();
        t2.join();
        long elapsedB = (System.nanoTime() - startB) / 1_000_000;

        System.out.println("  elapsed:         " + elapsedB + " ms");
        System.out.println("  ratio vs Part A: " + String.format("%.2fx", (double) elapsedB / elapsedA));
```

6. Locate **TODO 4.3**.

    Part C: two parallel pipelines, each in its own dedicated `ForkJoinPool`. No contention.
    update the pool size from the number given by `ForkJoinPool.commonPool().getParallelism()`, split it equally on each pool

7. Paste this snippet:

```java
        Thread.sleep(6_000);
        System.out.println("  --- Part C: two parallel pipelines, dedicated pools ---");

        ForkJoinPool pool1 = new ForkJoinPool(4);
        ForkJoinPool pool2 = new ForkJoinPool(4);

        long startC = System.nanoTime();
        ForkJoinTask<List<String>> task1 = pool1.submit(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        ForkJoinTask<List<String>> task2 = pool2.submit(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        task1.get();
        task2.get();
        long elapsedC = (System.nanoTime() - startC) / 1_000_000;

        pool1.shutdown();
        pool2.shutdown();

        System.out.println("  elapsed:         " + elapsedC + " ms");
        System.out.println("  ratio vs Part A: " + String.format("%.2fx", (double) elapsedC / elapsedA));
```

8. Locate **TODO 4.4**.

9. Uncomment `case "4" -> activity4();` in `main()`.

10. Compile and run. Select **4**.

11. Observe:
    - The first line prints your common pool's parallelism. If it's 1, your container is single-threaded and the ratios below will be less dramatic — the lesson still holds but the numbers compress.
    - Part A gives you the baseline: one pipeline, whole pool, fastest possible time for this workload.
    - Part B's elapsed time is roughly **often 10× to 50×** depending on how long Part A took and how loaded your machine. Two pipelines sharing the same pool cannot both run at full speed — they serialise through the shared workers. This is the contention.
    - Part C's elapsed time is roughly **2x** Part A. Two pipelines in two dedicated pools run genuinely in parallel. Each pool is isolated; neither pipeline waits for the other.
    - The B-to-C difference is the whole point. Same pipelines, same data, same JVM — only the pool assignment changed. Parallelism is a shared resource; shared resources contend.
    - **Question:** The JVM defaults to sharing one `commonPool` across all `.parallelStream()` callers. That choice caused the contention in Part B. Why would the JVM authors make this default — what problem would giving each `.parallelStream()` call its own fresh pool create?

---

## Lab Complete

Performance in stream pipelines is not abstract. Boxing is a measurable tax on every `Stream<Integer>`. Method references are a compile-time guarantee that no extra logic is hiding inside a one-step lambda. Checked exceptions and `java.util.function` are incompatible by design — the fix is a six-line custom `@FunctionalInterface` that you write once and reuse forever. And `.parallelStream()` is not free concurrency; it is free *sharing* of a scarce, JVM-wide resource that any other thread in the JVM can starve. Every one of these becomes a production incident the first time it's ignored.

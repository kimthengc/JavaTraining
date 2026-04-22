# Lab 5.2 — Structured Concurrency with Loom

**Module:** 5 — Virtual Threads & Project Loom
**Files:** `Lab_5_2_StructuredConcurrency.java`

> **Preview-flag callout**
>
> Both `ScopedValue` (JEP 446) and `StructuredTaskScope` (JEP 453) are **preview APIs** in Java 21. The compiler and the runtime both refuse to touch preview code unless explicitly told it's allowed. Compile and run with:
>
> - `javac --enable-preview --release 21 Lab_5_2_StructuredConcurrency.java`
> - `java --enable-preview Lab_5_2_StructuredConcurrency`
>
> Without `--enable-preview`, `javac` errors out with "preview feature is disabled by default" and the JVM refuses to load the class file. The compiler's note "uses preview features of Java SE 21" is harmless and confirms preview mode is active — leave it visible, it's the receipt.
>
> If VSCode compiles to a `bin` folder, the run command becomes `java -cp .\bin --enable-preview Lab_5_2_StructuredConcurrency`. Easiest path is to `cd` into the source folder and use plain `javac` / `java`.

---

## Activity 1: ScopedValue Replacing ThreadLocal

*The Ambiguity* — `ThreadLocal` has carried per-thread context in Java since 1.2. Web frameworks use it for the current user, transactional libraries use it for the active transaction, MDC libraries use it for log correlation. It works. But its API surface lets you do things that quietly break under virtual-thread scale, and the type system gives you no warning when you do.

`ThreadLocal` is mutable — any code that holds the reference can call `.set(...)` at any time, from any depth in the call stack. With one thread per request and a few thousand threads in the system, that's a manageable hazard. With one virtual thread per request and millions of them, the same patterns become memory and correctness landmines: `InheritableThreadLocal` copies values into every child thread; values persist for the lifetime of the underlying carrier and can leak across requests if the VT-to-task mapping isn't strict; cleanup requires explicit `.remove()` calls that are easy to forget.

`ScopedValue` is the Loom-era answer. Two API-shape decisions are the entire lesson. First, there is no `set` method — the `ScopedValue` type literally does not declare one. The compiler enforces immutability before any value flows. Second, the value's lifetime is bound to a lexical block: `ScopedValue.where(KEY, value).run(() -> { ... })`. Inside the block, `KEY.get()` returns the value. Outside the block, `KEY.get()` throws `NoSuchElementException`. There is no analogue of "I forgot to call `.remove()`" because the binding doesn't exist after `run` returns. Both guarantees come from the API shape — the compiler enforces one, the runtime enforces the other. This activity makes both visible.

### Steps

1. Open `Lab_5_2_StructuredConcurrency.java`.

2. Read the **Preview-flag callout** in the class Javadoc — Activities 1 and 2 both depend on `--enable-preview`. Read the Activity 1 scaffolding comment block. Note the side-by-side `ThreadLocal` vs `ScopedValue` API surface — this is the contrast each part of the activity makes concrete. Note that `AC1_USER` is declared as `ScopedValue.newInstance()` and that `ac1_readUserDeepInCallStack()` reads `AC1_USER.get()` with no parameter — this is what "implicit context" looks like.

3. Locate **TODO 1.1**.

    Paste a `ThreadLocal<String>` field called `AC1_USER_TL` and a method `ac1_threadLocalDemo()` that demonstrates the old-way pattern: set the value to `"alice"`, read it, read it again from a deeper call frame, mutate it to `"bob"` mid-flight to show that any code can change it, then call `.remove()` for manual cleanup. Also paste the `ac1_readUserDeepInCallStackTL()` helper that mirrors `ac1_readUserDeepInCallStack()` but reads the `ThreadLocal` instead.

4. Paste this snippet:

```java
private static final ThreadLocal<String> AC1_USER_TL = new ThreadLocal<>();

private static void ac1_threadLocalDemo() {
    AC1_USER_TL.set("alice");
    System.out.println("    [outer]     current user: " + AC1_USER_TL.get());
    ac1_readUserDeepInCallStackTL();
    AC1_USER_TL.set("bob");        // any code can mutate it, anywhere
    System.out.println("    [outer]     current user: " + AC1_USER_TL.get() + "  <-- mutated mid-flight");
    AC1_USER_TL.remove();          // manual cleanup required to avoid leaks
}

private static void ac1_readUserDeepInCallStackTL() {
    System.out.println("    [deep call] current user: " + AC1_USER_TL.get());
}
```

5. Locate **TODO 1.2**.

    Paste a method `ac1_scopedValueBroken()` that attempts the wrong thing — calling `AC1_USER.set("alice")` as if it were a `ThreadLocal`. The compiler will reject this. Leave the method UNCOMMENTED so the rejection happens for real.

6. Paste this snippet:

```java
private static void ac1_scopedValueBroken() {
    AC1_USER.set("alice");                                  // <-- compiler rejects: no method set(String)
    System.out.println("    current user: " + AC1_USER.get());
}
```

7. Compile with the preview flag.

    `javac --enable-preview --release 21 -d .\bin .\module-05-virtual-threads\src\Lab_5_2_StructuredConcurrency.java`

    The compiler should reject the `AC1_USER.set("alice")` line with:

    ```
    error: cannot find symbol
            AC1_USER.set("alice");
                    ^
      symbol:   method set(String)
      location: variable AC1_USER of type ScopedValue<String>
    ```

    This is the type-level half of the lesson. There is no `set(...)` method on `ScopedValue` to find — immutability is enforced before any value flows. Read the error, then comment the entire `ac1_scopedValueBroken()` method out (or wrap its body in `/* ... */`) so the file compiles for the next step.

8. Locate **TODO 1.3**.

    Paste two methods. `ac1_scopedValueDemo()` does the right thing: bind `AC1_USER` to `"alice"` inside a `where(...).run(...)` block, read the value at the outer level, then call the deep helper to show the value flows implicitly through the call stack. `ac1_outsideScopeDemo()` reads the value *outside* any scope to show the runtime half of the lesson — `isBound()` returns false and `get()` throws `NoSuchElementException`. The import for `NoSuchElementException` should already be present at the top of the file; if your IDE removed it on save, add it back manually.

9. Paste this snippet:

```java
private static void ac1_scopedValueDemo() {
    ScopedValue.where(AC1_USER, "alice").run(() -> {
        System.out.println("    [outer]     current user: " + AC1_USER.get());
        ac1_readUserDeepInCallStack();
    });
}

private static void ac1_outsideScopeDemo() {
    System.out.println("    AC1_USER.isBound() outside any scope: " + AC1_USER.isBound());
    try {
        String leaked = AC1_USER.get();
        System.out.println("    leaked value: " + leaked);  // unreachable
    } catch (NoSuchElementException e) {
        System.out.println("    AC1_USER.get() outside scope threw: " + e.getClass().getSimpleName());
    }
}
```

10. Locate **TODO 1.4**.

11. Uncomment the `activity1()` method.

12. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

13. Compile and run with the preview flag. Select **1**.

    `javac --enable-preview --release 21 -d .\bin .\module-05-virtual-threads\src\Lab_5_2_StructuredConcurrency.java`
    `java --enable-preview -cp .\bin Lab_5_2_StructuredConcurrency`

14. Observe:
    - **Part A** (ThreadLocal): the `[outer]` reads "alice", the `[deep call]` also reads "alice" — the value flows through the stack via the implicit thread-local channel. Then a second `[outer]` line shows the value mutated to "bob" — *any* code holding the `AC1_USER_TL` reference could have done that, from any depth, with no compile-time signal. This is the "any code can mutate it, anywhere" hazard that ThreadLocal's API surface allows.
    - **Part B** (ScopedValue): the `[outer]` reads "alice", the `[deep call]` reads "alice" — the value flows through the stack the same way the ThreadLocal version did. The functional behaviour for *reading* is identical. The difference isn't visible in the output of Part B alone — it was visible in the compiler error you saw at step 7. There is no setter to misuse.
    - **Part C** (lifetime boundary): outside the `where(...).run(...)` block, `AC1_USER.isBound()` returns `false` and `AC1_USER.get()` throws `NoSuchElementException`. The binding does not exist outside the lexical scope. There is no `.remove()` to forget — the runtime guarantees cleanup.
    - The two guarantees together: the compiler refuses to let you mutate the value, and the runtime refuses to let it leak past the scope. ThreadLocal gave you neither.
    - **Question**: With ThreadLocal, the most common production bug isn't a logic error — it's a forgotten `.remove()` that leaks a request-scoped value into the next request that happens to be served on the same pooled thread. Walk through the ScopedValue API and explain, mechanically, why that bug class cannot exist for `ScopedValue` regardless of whether the executor is a fixed pool, a virtual-thread-per-task executor, or anything else. What specifically is the runtime doing at the end of `run(...)` that makes the leak impossible?

---

## Activity 2: StructuredTaskScope — Treating N Tasks as One

*The False Promise* — Lab 4.3 Activity 3 used `CompletableFuture.allOf(...)` to fan out three remote calls in parallel and wait for all of them. It worked. The composition was fluent, the throughput was good, and the code looked clean. But that pattern carries a quiet structural problem that doesn't surface until something goes wrong: there is no relationship between the three futures beyond "I happen to be waiting on all of them." If one throws, the other two keep running. If you abandon the `allOf` and bail out of the calling method, the abandoned futures are still consuming threads, still issuing HTTP calls, still holding connections. There's no parent task that owns them — `allOf` is a *combinator*, not a *container*. The framework gave you fan-out but no boundary, no lifetime, no cancellation.

`StructuredTaskScope` is the boundary. The pattern is `try (var scope = new StructuredTaskScope.ShutdownOnFailure()) { ... }`. Inside the `try`-block, you `fork` subtasks — each one runs in its own virtual thread. You then call `scope.join()` to wait for either all subtasks to finish or the first one to fail. The "policy" is `ShutdownOnFailure`: the moment one subtask throws, the scope shuts down and *cancels* the still-running siblings. Cancellation is real — the carrier interrupts the cancelled VTs, they stop running, they don't waste any more time on work whose result will be discarded. The `try`-block is the lifetime; the scope is the parent; the `fork` calls are the children. This is what was missing from `CompletableFuture` composition.

This activity demonstrates both halves. The happy path runs three "remote calls" with different durations (300ms, 600ms, 900ms) inside a `ShutdownOnFailure` scope and verifies that elapsed time tracks the longest sibling — same fan-out semantics as `allOf`. The failure path replaces the middle task with a variant that throws after 200ms and verifies that the still-running siblings are cancelled mid-flight: their final `state()` reads `UNAVAILABLE` (not `SUCCESS`, not `FAILED`), and elapsed time tracks the failure point (~200ms), not what the longest sibling would have taken (~900ms). The cancellation is the lesson.

### Steps

1. Read the Activity 2 scaffolding. Note the four task helpers — `ac2_fetchProfile` (300ms), `ac2_fetchOrders` (600ms), `ac2_fetchRecommendations` (900ms), and `ac2_fetchOrdersFailing` (throws at 200ms). The sleeps are stand-ins for blocking I/O against three independent backend services. The different durations are deliberate: they make sibling cancellation visible in the timing in the failure path.

2. Locate **TODO 2.1**.

    Paste a method `ac2_runHappyPath()` that opens a `StructuredTaskScope.ShutdownOnFailure` in a try-with-resources, forks all three of the *successful* fetch helpers, calls `scope.join()` and `scope.throwIfFailed()`, then reads each `Subtask.get()` and prints the result. Time the whole block and print the elapsed ms.

3. Paste this snippet:

```java
private static void ac2_runHappyPath() throws Exception {
    long start = System.nanoTime();
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        Subtask<String> profile = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchProfile);
        Subtask<String> orders  = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchOrders);
        Subtask<String> recs    = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchRecommendations);

        scope.join();
        scope.throwIfFailed();

        System.out.println("    profile: " + profile.get());
        System.out.println("    orders : " + orders.get());
        System.out.println("    recs   : " + recs.get());
    }
    long elapsed = (System.nanoTime() - start) / 1_000_000;
    System.out.println("    elapsed: " + elapsed + " ms");
}
```

4. Locate **TODO 2.2**.

    Paste a method `ac2_runFailurePath()` that mirrors the happy-path structure but forks `ac2_fetchOrdersFailing` instead of `ac2_fetchOrders`. Instead of calling `subtask.get()` on each result, print each subtask's `.state()` after `join()` returns — this is what reveals the cancellation. Wrap `scope.throwIfFailed()` in a try/catch so the failure detail can be printed without crashing the menu. Time the whole block.

5. Paste this snippet:

```java
private static void ac2_runFailurePath() throws Exception {
    long start = System.nanoTime();
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        Subtask<String> profile = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchProfile);
        Subtask<String> orders  = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchOrdersFailing);
        Subtask<String> recs    = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchRecommendations);

        scope.join();   // waits for all to finish OR for first failure to trigger shutdown

        System.out.println("    profile state: " + profile.state());
        System.out.println("    orders  state: " + orders.state());
        System.out.println("    recs    state: " + recs.state());

        try {
            scope.throwIfFailed();
        } catch (Exception e) {
            System.out.println("    scope reported failure: "
                + e.getClass().getSimpleName()
                + " caused by "
                + e.getCause().getClass().getSimpleName()
                + ": " + e.getCause().getMessage());
        }
    }
    long elapsed = (System.nanoTime() - start) / 1_000_000;
    System.out.println("    elapsed: " + elapsed + " ms");
}
```

6. Locate **TODO 2.3**.

7. Uncomment the `activity2()` method.

8. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

9. Compile and run with the preview flag. Select **2**.

    `javac --enable-preview --release 21 -d .\bin .\module-05-virtual-threads\src\Lab_5_2_StructuredConcurrency.java`
    `java --enable-preview -cp .\bin Lab_5_2_StructuredConcurrency`

10. Observe:
    - **Happy path**: elapsed is approximately 900–1000ms, give or take a few tens of milliseconds for VT spawn overhead. The longest task is 900ms (`recs`). All three ran concurrently — total wall-clock ≈ longest single task. This matches what `CompletableFuture.allOf(...)` would have produced. For successful fan-out, the structured version has the same timing profile as the unstructured one.
    - **Failure path elapsed**: approximately 200–300ms. The failing task threw at 200ms; the elapsed time tracks the failure point, not the longest sibling. If cancellation were *not* real, elapsed would be ≈900ms (the recs task would have run to completion before `join()` returned). The fact that it isn't is the proof that cancellation actually stopped the siblings mid-flight.
    - **Failure path subtask states**: `orders` reads `FAILED` — it threw, the scope captured the exception. `profile` and `recs` read `UNAVAILABLE` — they were neither successful nor failed. The scope cancelled them while they were still sleeping. `UNAVAILABLE` is the API's way of saying "this subtask never produced a result, by design." If you tried to call `.get()` on either of these, you'd get an `IllegalStateException` ("Result is unavailable or subtask did not complete successfully") — the API actively prevents you from accidentally consuming a result that doesn't exist.
    - **Failure reporting**: `scope.throwIfFailed()` wraps the original exception in `ExecutionException`. Unwrap with `.getCause()` to get the actual `RuntimeException("orders service unavailable")` that the failing task threw. This is the standard pattern for surfacing the underlying cause to the caller.
    - **The closing-note variant**: this lab uses `ShutdownOnFailure` because the demo case is "we need all three results — abort everything if any one fails." There's a sibling policy `StructuredTaskScope.ShutdownOnSuccess<T>` for the opposite case — "we need any one result, abort the rest as soon as the first one wins." It's the same try-with-resources structure, the same `fork`/`join` lifecycle; the only differences are the constructor and that you call `scope.result()` at the end instead of `scope.throwIfFailed()`. The classic use case is racing a primary data source against a replica and taking whichever responds first.
    - **Question**: In Lab 4.3 Activity 3, `CompletableFuture.allOf(profile, orders, recs).join()` would have waited the full 900ms for `recs` even when `orders` had already failed at 200ms — *and* `recs` would have continued running, holding its connection, doing its work, after the calling code had given up. Walk through what actually happens to that abandoned `CompletableFuture`: who owns it, when does it stop, what happens to its result. Then compare to what `StructuredTaskScope.ShutdownOnFailure` did to `recs` in this activity. Why is "the parent owns the children" a stronger guarantee than "the framework offers a way to compose them"?

---

## Lab Complete

Both activities tell the same structural story: the *boundary* is what was missing. `ScopedValue` bounds the lifetime of a piece of context to a lexical block — the value cannot leak past `run(...)` because the runtime tears the binding down when the block exits. `StructuredTaskScope` bounds the lifetime of a group of concurrent subtasks to a try-with-resources block — the children cannot outlive the parent because the scope cancels them when the block exits. Both are answers to a generation of bugs caused by data and threads that outlived the code that created them. With virtual threads making it cheap to spawn millions of concurrent operations, having a bounded structure to put them in stops being a nicety and starts being the only way the model is tractable. The discipline isn't optional — it's how you keep the abstraction honest at scale.

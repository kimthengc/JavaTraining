# Lab 4.1 — Shared State & Memory Visibility

**Module:** 4 — Concurrency Fundamentals & Async Architecture
**Files:** `Lab_4_1_SharedState.java`
**Compile:** `javac Lab_4_1_SharedState.java`
**Run:** `java Lab_4_1_SharedState`

---

## Activity 1: The Lost Update

*The Trap* — Most developers can recite "`counter++` isn't atomic" from memory. Far fewer have ever watched their own terminal print the wrong number. This activity closes that gap. You will run a two-thread increment loop, see the final count come out wrong, and then fix it with `synchronized`. A subtle point surfaces immediately: the shared counter is declared `volatile`, and the race happens anyway. `volatile` gives you visibility, not atomicity. This distinction is Activity 2's subject; here it protects us from a JIT optimisation that would otherwise narrow the race window and hide the bug on repeated runs.

`counter++` looks like one operation in source code. It compiles to three: read the field into a register, add one, write the register back to the field. Any other thread can slip in between the read and the write, read the same stale value, and both threads end up writing the same incremented number back. One increment silently vanishes. Over 200,000 iterations across two threads, you will lose somewhere between a handful and tens of thousands of increments, and the exact number changes every run because it depends on how the OS scheduler interleaves the two threads. Without `volatile`, the JIT would eventually optimise the field access into a register-local variable and close the race window — the bug would still be there, just unreachable. Marking the field `volatile` forces a real memory read and a real memory write on every increment, keeping the race window wide.

### Steps

1. Open `Lab_4_1_SharedState.java`.

2. Read the scaffolding section at the top. Note the `ac1_counter` field declared as `volatile int`, and the `INCREMENTS_PER_THREAD` constant set to 100,000. Two threads will each run that many increments, so the expected total is 200,000. The inline comment explains why `volatile` is here: to keep the race window observable across repeated runs. It does not fix the race — the next step demonstrates that.

3. Locate **TODO 1.1**.

    Create a method called `ac1_increment` that loops `INCREMENTS_PER_THREAD` times and increments `ac1_counter` on each iteration. No locking. No printing inside the loop — printing is synchronised internally and would mask the bug.

4. Paste this snippet:

```java
private static void ac1_increment() {
    for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
        ac1_counter++;
    }
}
```

5. Locate **TODO 1.2**.

6. Uncomment the `activity1()` method body.

7. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

8. Compile and run. Select **1**.

9. Observe the final count. It is not 200,000. Run it **three or four more times** without changing anything. Each run produces a different wrong number.

10. Locate **TODO 1.3**.

    Replace the body of `ac1_increment` with a version that wraps the increment in a `synchronized` block on a shared lock object. The scaffolding has provided `AC1_LOCK` for exactly this purpose.

11. Paste this snippet, replacing the existing `ac1_increment` method:

```java
private static void ac1_increment() {
    for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
        synchronized (AC1_LOCK) {
            ac1_counter++;
        }
    }
}
```

12. Compile and run. Select **1**.

13. Observe:
    - Before the fix, the count was wrong and different every run. The OS scheduler interleaves the two threads non-deterministically, so the exact number of lost updates is unpredictable.
    - `volatile` did not save you. The field was volatile from the start, and the race happened anyway. `volatile` makes writes visible to other threads — it does not make `read-modify-write` atomic.
    - After the `synchronized` fix, the count is exactly 200,000 every run. `synchronized` makes the read-modify-write sequence atomic with respect to any other thread holding the same lock.
    - The fix did not remove the race — it serialised the critical section. Only one thread can be inside the `synchronized` block at a time.
    - **Question:** The fix works, but what does it cost? If this counter were incremented a billion times a second across 32 threads, would you still reach for `synchronized`? What would you reach for instead, and why?

---

## Activity 2: The Invisible Write

*The False Promise* — "Effectively final" protects a variable reference. It does not protect what threads can *see* about a shared field. A writer thread can set a flag to `true`, finish, exit and a reader thread in another core can loop on that flag forever, reading a stale `false` from its own view of the variable. The compiler will not warn you. The code will compile, run, and hang. This activity makes that hang happen, on purpose, in your terminal.

Most explanations blame CPU caches. That is not the real culprit on modern x86 hardware, which has a strong memory model that usually makes cross-core writes visible quickly. The actual villain is the JIT compiler. When the JIT sees a non-volatile field read inside a tight loop with no other memory operations, it proves that nothing in the loop body modifies the field and hoists the read out of the loop entirely. Your `while (!stopRequested) { ... }` gets rewritten as roughly `if (!stopRequested) while (true) { ... }`. No amount of flag-setting from the writer thread will ever reach the reader, because the reader is no longer reading. Marking the field `volatile` tells the JIT "you may not optimise reads of this field" — the read goes back into the loop body, the reader sees the update, and the thread exits.

### Steps

1. In `Lab_4_1_SharedState.java`, read the Activity 2 scaffolding. Note the `ac2_stopRequested` field declared as a plain `boolean` — no `volatile` keyword. There is also a `WATCHDOG_TIMEOUT_MS` constant set to 3000. The reader thread is started as a daemon, and `activity2()` waits at most 3 seconds for it to exit on its own. If the reader is still running after that, the method returns anyway and prints a diagnostic. The reader keeps spinning in the background, but because it is a daemon thread, it will not prevent the JVM from exiting when you quit the lab.

2. Locate **TODO 2.1**.

    Create two `Runnable` fields: `ac2_reader` loops tightly on `!ac2_stopRequested` and counts its iterations into `ac2_iterations`. `ac2_writer` sleeps for 500ms, then sets `ac2_stopRequested = true`.

3. Paste this snippet:

```java
private static final Runnable ac2_reader = () -> {
    long iterations = 0;
    while (!ac2_stopRequested) {
        iterations++;
    }
    ac2_iterations = iterations;
};

private static final Runnable ac2_writer = () -> {
    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
    ac2_stopRequested = true;
    System.out.println("  [writer] flag set at t=500ms");
};
```

4. Locate **TODO 2.2**.

5. Uncomment the `activity2()` method body.

6. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

7. Compile and run. Select **2**.

8. Observe: the writer reports setting the flag at 500ms. The reader does not exit. After 3000ms, `activity2()` gives up waiting and prints `reader STILL RUNNING after 3000ms — JIT hoisted the read, writer's flag is invisible`. The menu returns. The reader is still spinning in the background, but as a daemon thread it imposes no cost on lab completion and will die when the JVM exits.

9. Locate **TODO 2.3**.

    Change the `ac2_stopRequested` field declaration to add the `volatile` modifier. This is a one-word edit to the existing field, not a new paste — the TODO marker sits on the line above the field.

10. Paste this snippet, replacing the existing declaration:

```java
private static volatile boolean ac2_stopRequested = false;
```

11. Compile and run. Select **2**.

12. Observe:
    - Before `volatile`: the reader never exited. The writer's `stopRequested = true` was invisible to the reader's hoisted view of the field, and the activity had to abandon the reader after 3 seconds.
    - After `volatile`: the reader exits within milliseconds of the writer's set. Elapsed time is approximately 500ms — the 500ms sleep plus negligible loop overhead.
    - The JIT is allowed to hoist reads of non-volatile fields out of tight loops. `volatile` forbids that optimisation and inserts the memory barriers needed to make the write visible across threads.
    - **Question:** `synchronized` would also have fixed this. Why reach for `volatile` instead? What is the cost difference, and what does `volatile` explicitly *not* give you that `synchronized` does?

---

## Activity 3: Three Ways to Be Thread-Safe

*The Ambiguity* — `synchronized`, `volatile`, and `AtomicInteger` all claim to make concurrent code "thread-safe." They are not interchangeable. Each has a specific job, a specific cost, and a specific failure mode when misapplied. This activity runs the same increment workload against three different implementations and measures both correctness and throughput, so you can see the trade-offs instead of arguing about them.

The naive version has no synchronisation at all and will produce a wrong answer — often quickly, sometimes not, because cache-coherency traffic between cores is not free even without a lock. 

The synchronised version wraps every method in a mutex. 

The atomic version uses a hardware compare-and-swap instruction under the hood — no lock, just a CPU-level atomic operation that either succeeds in one step or retries on contention. 

Under high contention on a single hot variable, synchronised and atomic perform within the same order of magnitude, and which one wins on any given run depends on thread count, core count, and JIT state. 

Run the activity several times and you will see the two "correct" timings trade places. The lesson is not "atomics win." The lesson is that thread-safety is a design decision with a cost curve: `synchronized` wins when you need compound invariants over multiple fields, atomics win when contention is low and the operation is genuinely single-variable, and "it'll figure itself out" wins nothing.

### Steps

1. Read the Activity 3 scaffolding. Note the three empty class stubs: `NaiveCounter`, `SynchronisedCounter`, `AtomicCounter`, all implementing a shared `Counter` interface with `increment()` and `get()`. The benchmark workload is 8 threads, 250,000 increments each, expected total 2,000,000.

2. Locate **TODO 3.1**.

    Fill in the three counter implementations. `NaiveCounter` uses a plain `int`. `SynchronisedCounter` uses a plain `int` but both methods are `synchronized`. `AtomicCounter` wraps an `AtomicInteger`. These three are a single cohesive paste — they are meant to be read side by side as a comparison.

3. Paste this snippet, replacing the three empty class bodies:

```java
static final class NaiveCounter implements Counter {
    private int value = 0;
    public void increment() { value++; }
    public int get() { return value; }
}

static final class SynchronisedCounter implements Counter {
    private int value = 0;
    public synchronized void increment() { value++; }
    public synchronized int get() { return value; }
}

static final class AtomicCounter implements Counter {
    private final AtomicInteger value = new AtomicInteger(0);
    public void increment() { value.incrementAndGet(); }
    public int get() { return value.get(); }
}
```

4. Locate **TODO 3.2**.

    Create a method called `ac3_benchmark` that runs a given `Counter` with 8 threads, each performing 250,000 increments, and returns an array `{finalCount, elapsedMillis}`. The scaffolding has constants `AC3_THREADS` and `AC3_INCREMENTS_PER_THREAD` already defined.

5. Paste this snippet:

```java
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

    for (Thread t : threads) t.join();

    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    return new long[] { counter.get(), elapsedMs };
}
```

6. Locate **TODO 3.3**.

7. Uncomment the `activity3()` method body.

8. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

9. Compile and run. Select **3**.

10. Observe the printed table. Three rows, three outcomes:
    - `NaiveCounter`: final count is less than 2,000,000, usually far less. Speed varies — not always the fastest, because cache-line contention costs real cycles even without a lock.
    - `SynchronisedCounter`: exactly 2,000,000. Timing competitive with the atomic version under this workload.
    - `AtomicCounter`: exactly 2,000,000. Timing competitive with synchronised — the two trade places across runs. CAS(Compare and Swap) avoids lock acquisition but pays in retries when contention is high.
    - Run Activity 3 several times. Notice that which of the two correct implementations is "faster" changes from run to run. Neither has a decisive edge on this workload.
    - **Question:**  If the two correct implementations perform within noise of each other here, when does the choice between them matter? Design two scenarios: one where `synchronized` is clearly the right pick, and one where `AtomicInteger` is clearly the right pick. What property of the workload decides?

---

## Lab Complete

These three activities cover three distinct failure modes: atomicity (Activity 1, `synchronized` fixes it), visibility (Activity 2, `volatile` fixes it), and the design choice between them (Activity 3, pick the tool that matches the critical section). `volatile` gives you visibility without atomicity. `synchronized` gives you both, at a cost. Atomics give you both for a single variable, cheaply. There is no single right answer — there is a trade-off curve, and your job is to know where on it you are sitting.

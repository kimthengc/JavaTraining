# Lab 2.2 — Stream Internals & Collectors

**Module:** 2 — Functional Architecture & Stream Internals
**Files:** `Lab_2_2_StreamInternals.java`, `Order.java`

---

## Activity 1: Laziness Is Real

*The Illusion* — When you read `.filter().map().collect()`, it's natural to picture data flowing through stages like a conveyor belt: every item filtered, then every item mapped, then collected. 

That mental model is wrong on two counts. First, nothing flows at all until a terminal operation is attached — intermediate operations only build a plan. Second, when data *does* flow, it goes element-by-element through the whole pipeline, not stage-by-stage through the whole dataset.

Streams are lazy. `Stream<T>` is a recipe, not a collection. `.filter()` and `.map()` return new `Stream<T>` objects that wrap the previous one — they describe work, they don't perform it. 

Only terminal operations (`.collect()`, `.findFirst()`, `.count()`, `.forEach()`) actually pull elements through the pipeline. Until a terminal op is called, the entire chain sits dormant. 

This is why `.peek()` exists — it's the one intermediate op designed specifically to let you *observe* pipeline execution without changing it, so you can see exactly when (and whether) a stage runs.

### Steps

1. Open `Lab_2_2_StreamInternals.java`.

2. Read the `Order.SAMPLE` list in `Order.java` — 18 orders across 3 regions and 4 categories. Every activity in this lab uses this dataset.

3. Locate **TODO 1.1**.

    Create a method called `ac1_pipelineWithoutTerminal` that builds a pipeline over `Order.SAMPLE` with a `.filter()`, a `.map()`, `.peek()` loggers on each stage — and **no terminal operation**.

4. Paste this snippet:

```java
    private static void ac1_pipelineWithoutTerminal() {
        Order.SAMPLE.stream()
                .peek(o -> System.out.println("    filter peek: " + o.customer()))
                .filter(o -> o.amount() > 500)
                .peek(o -> System.out.println("    map peek:    " + o.customer()))
                .map(Order::customer);
        // Notice: no terminal op. Nothing should run.
    }
```

5. Locate **TODO 1.2**.

    Create a method called `ac1_pipelineWithTerminal` with the same pipeline shape, but ending in `.collect(toList())` and printing the result.

6. Paste this snippet:

```java
    private static void ac1_pipelineWithTerminal() {
        List<String> result = Order.SAMPLE.stream()
                .peek(o -> System.out.println("    filter peek: " + o.customer()))
                .filter(o -> o.amount() > 500)
                .peek(o -> System.out.println("    map peek:    " + o.customer()))
                .map(Order::customer)
                .collect(toList());
        System.out.println("  result: " + result);
    }
```

7. Locate **TODO 1.3** (three occurrences — two in `activity1()`, one in `main()`).

8. Uncomment the two call lines inside `activity1()`.

9. Uncomment `case "1" -> activity1();` in `main()`.

10. Compile and run `Lab_2_2_StreamInternals`.

11. Select **1** from the menu.

12. Observe:
    - Part A prints no peek output at all. The pipeline was built — no compiler error, no runtime error — but not a single element moved through it. Stream chains are inert without a terminal op.
    - Part B prints the peek output *interleaved*: `filter peek` for an element, then `map peek` for the same element (if it survived the filter), then `filter peek` for the next element. Not all filters then all maps.
    - This reveals element-at-a-time processing. The stream is a pull model — each terminal-op pull drags one element through every stage before fetching the next.
    - **Question:** Why does the `.peek()` output interleave across stages instead of printing all filter logs first, then all map logs? What does that tell you about how streams differ from a traditional "loop, collect, loop again" implementation?

---

## Activity 2: Short-Circuiting on an Infinite Stream

*The Crack* — If streams were eager, `Stream.iterate(1, n -> n + 1)` would hang your JVM forever. It doesn't. You can build an infinite stream and pull from it safely — as long as your terminal operation knows when to stop. 

It's a direct consequence of how stream laziness is defined. But it changes what's expressible: "find the first X that satisfies Y" no longer requires pre-computing the search space.

Short-circuiting terminal operations (`.findFirst()`, `.findAny()`, `.anyMatch()`, `.allMatch()`, `.noneMatch()`, `.limit()`) signal to the pipeline that they can stop pulling as soon as their answer is determined. 

Non-short-circuiting terminals (`.count()`, `.collect()`, `.forEach()`, `.reduce()`) must — in general — pull every element. The pairing of lazy intermediates with short-circuiting terminals is what makes streams safe on unbounded sources. Miss this pairing, and the same code that processed three elements suddenly tries to process infinity.

### Steps

1. Locate **TODO 2.1**.

    Create a method called `ac2_infiniteShortCircuit` that builds an infinite stream of integers starting at 1, peeks each element with a counter, filters for multiples of 7, and finds the first one.

2. Paste this snippet:

```java
    private static void ac2_infiniteShortCircuit() {
        int[] pulled = {0};   // capture-safe counter (see Lab 2.1, Activity 4)

        Optional<Integer> first = Stream.iterate(1, n -> n + 1)
                .peek(n -> { pulled[0]++; System.out.println("    pulled: " + n); })
                .filter(n -> n % 7 == 0)
                .findFirst();

        System.out.println("  first multiple of 7: " + first.orElseThrow());
        System.out.println("  elements pulled from infinite stream: " + pulled[0]);
    }
```

3. Locate **TODO 2.2**.

    Create a method called `ac2_boundedFullPull` that uses `IntStream.rangeClosed(1, 20).boxed()`, filters out zero elements (keeps all), peeks each, and calls `.count()`. The filter is deliberate — without it, the JVM can optimise `.count()` on a sized stream and skip the peek entirely.

4. Paste this snippet:

```java
    private static void ac2_boundedFullPull() {
        long count = IntStream.rangeClosed(1, 20).boxed()
                .filter(n -> n > 0)   // forces the terminal to actually walk elements
                .peek(n -> System.out.println("    visited: " + n))
                .count();

        System.out.println("  count: " + count);
    }
```

5. Locate **TODO 2.3** (three occurrences — two in `activity2()`, one in `main()`).

6. Uncomment both call lines inside `activity2()`.

7. Uncomment `case "2" -> activity2();` in `main()`.

8. Compile and run. Select **2**.

9. Observe:
    - Part A pulls exactly seven elements from an infinite stream — 1 through 7. The moment the filter matches, `.findFirst()` stops the pull. The stream is infinite; the *work* is bounded.
    - Part B walks all 20 elements. `.count()` has no early-exit contract — it must know the total, so every element gets visited.
    - The shape of the peek output is the proof. Same intermediate ops (filter + peek). Different terminal ops. Different amounts of work.
    - **Question:** The infinite-stream pipeline uses `Stream.iterate(1, n -> n + 1)` — a genuinely unbounded sequence. Why doesn't this hang? What specific combination of stream properties makes this safe, and what single change would break it?

---

## Activity 3: Multi-Level Grouping

*The Ambiguity* — Every legacy Java codebase has variants of the same nested-loop grouping logic: build a `Map`, check if the key exists, create an inner `Map` if not, check the inner key, create an inner `List` if not, add the element. 

Five lines of mechanics to express "group orders by region, then by category." The intent drowns in the plumbing. Collectors turn this into one expression where the shape of the code matches the shape of the output.

`Collectors.groupingBy(classifier)` returns a `Map<K, List<T>>`. Its two-argument form `groupingBy(classifier, downstreamCollector)` lets you replace that `List<T>` with whatever the downstream collector produces — another `groupingBy` (nested grouping), a `summingDouble` (reduction per group), a `counting` (count per group), a `mapping` (transform per group), or any combination. 

This is the pattern: the downstream collector slot is a compositional socket. Anything that produces a `Collector` fits. That's what "collectors compose" means in practice.

Before you start, read the **LEGACY SCAFFOLDING** comment block at the top of `Lab_2_2_StreamInternals.java`. That's the imperative version of what Part A replaces. Don't paste it — just see what you're replacing.

### Steps

1. Read the **LEGACY SCAFFOLDING** block at the top of `Lab_2_2_StreamInternals.java`. Count the moving parts: two `computeIfAbsent` calls, two lambda constructors, one `.add()`. This is the baseline.

2. Locate **TODO 3.1**.

    Inside `activity3()`, Part A, build a two-level grouping: orders grouped by region, then by category. Print it with `ac3_print(...)`.

3. Paste this snippet:

```java
        Map<String, Map<String, List<Order>>> byRegionThenCategory =
                Order.SAMPLE.stream()
                        .collect(groupingBy(Order::region,
                                 groupingBy(Order::category)));
        ac3_print("region -> category -> orders", byRegionThenCategory);
```

4. Locate **TODO 3.2**.

    In Part B, replace the inner list with a sum of amounts. This shows the downstream slot isn't just "another groupingBy" — any collector fits.

5. Paste this snippet:

```java
        Map<String, Double> totalByRegion =
                Order.SAMPLE.stream()
                        .collect(groupingBy(Order::region,
                                 summingDouble(Order::amount)));
        ac3_print("region -> total amount", totalByRegion);
```

6. Locate **TODO 3.3**.

    In Part C, combine both patterns: group by region, then by category, and count the orders in each innermost bucket. Three collectors nested in one expression.

7. Paste this snippet:

```java
        Map<String, Map<String, Long>> countByRegionAndCategory =
                Order.SAMPLE.stream()
                        .collect(groupingBy(Order::region,
                                 groupingBy(Order::category,
                                 counting())));
        ac3_print("region -> category -> count", countByRegionAndCategory);
```

8. Locate **TODO 3.4** in `main()`.

9. Uncomment `case "3" -> activity3();`.

10. Compile and run. Select **3**.

11. Observe:
    - Part A produces the exact `Map<String, Map<String, List<Order>>>` the legacy nested-loop built — but in a single expression whose shape mirrors the output type.
    - Part B produces a `Map<String, Double>`. The orders themselves aren't in the output — they were consumed by `summingDouble` to produce the total. The downstream collector decides the value type.
    - Part C produces `Map<String, Map<String, Long>>`. Three nested collectors, three nested map levels. The code shape tracks the data shape exactly.
    - **Question:** In Part B, `summingDouble(Order::amount)` produced a `Double` per region — but the orders themselves disappeared from the output. Where did they go? Can you describe what `summingDouble` does as a collector, in terms of what it accumulates and what it emits?

---

## Activity 4: Partitioning + Custom Downstream

*The Illusion* — `partitioningBy(predicate)` looks like `groupingBy` with a boolean classifier — same shape, same usage, same downstream-collector support. 

Most of the time it behaves identically. But the two are not interchangeable, and the difference matters precisely when you least expect it: when a partition happens to be empty.

`partitioningBy` guarantees both `true` and `false` keys exist in the result map, even if one (or both) maps to an empty collection. 

`groupingBy(booleanFunction)` only includes keys for which elements were actually classified. 

So a query that returns "no high-value orders" gives you `{false=[...], true=[]}` with `partitioningBy` and `{false=[...]}` with `groupingBy` — missing the `true` key entirely. 

The partition API is designed around the contract "always two buckets." The group API is designed around "as many buckets as the data produced." 

Choose based on the contract you want downstream consumers to see.

### Steps

1. Locate **TODO 4.1**.

    In `activity4()` Part A, partition the full dataset into high-value (> 1000) and low-value orders.

2. Paste this snippet:

```java
        Map<Boolean, List<Order>> byValue =
                Order.SAMPLE.stream()
                        .collect(partitioningBy(o -> o.amount() > 1000));
        ac3_print("amount > 1000 -> orders", byValue);
```

3. Locate **TODO 4.2**.

    In Part B, run both `partitioningBy` and `groupingBy(boolean)` against `LOW_VALUE_ONLY` — a list where no order exceeds 1000. The difference in the result maps is the whole point of this part.

4. Paste this snippet:

```java
        Map<Boolean, List<Order>> partitioned =
                LOW_VALUE_ONLY.stream()
                        .collect(partitioningBy(o -> o.amount() > 1000));
        ac3_print("partitioningBy (low-value only)", partitioned);

        Map<Boolean, List<Order>> grouped =
                LOW_VALUE_ONLY.stream()
                        .collect(groupingBy(o -> o.amount() > 1000));
        ac3_print("groupingBy     (low-value only)", grouped);
```

5. Locate **TODO 4.3**.

    In Part C, partition the full dataset and transform each bucket into a list of customer names instead of orders. This is `mapping` as a downstream collector — the same compositional socket you used in Activity 3.

6. Paste this snippet:

```java
        Map<Boolean, List<String>> customersByValue =
                Order.SAMPLE.stream()
                        .collect(partitioningBy(
                                o -> o.amount() > 1000,
                                mapping(Order::customer, toList())));
        ac3_print("amount > 1000 -> customer names", customersByValue);
```

7. Locate **TODO 4.4** in `main()`.

8. Uncomment `case "4" -> activity4();`.

9. Compile and run. Select **4**.

10. Observe:
    - Part A produces a map with both `true` and `false` keys, each holding the matching orders. Expected shape.
    - Part B is the crux. `partitioningBy` against the low-value-only list produces `{false=[...], true=[]}` — the `true` key is present with an empty list. `groupingBy` against the same list produces `{false=[...]}` — no `true` key at all. Same data, same predicate, different contract.
    - Part C shows `mapping(Order::customer, toList())` slotted in as the downstream collector — the orders were transformed into customer names *during* partitioning, not after. The same downstream-collector socket from Activity 3 works identically here.
    - **Question:** If `partitioningBy` always has both keys and `groupingBy` with a boolean function doesn't, when would you deliberately choose `groupingBy(boolean)` over `partitioningBy`? Is there ever a legitimate reason, or is `partitioningBy` always the safer default for boolean classification?

---

## Lab Complete

Streams look declarative, but they're a pipeline with concrete mechanics. Intermediate operations build a plan that only runs when a terminal operation pulls — laziness is observable, not theoretical. Short-circuiting terminals make unbounded sources safe, but only in combination with the right intermediates. Collectors compose through the downstream-collector slot, turning nested data shapes into single expressions. And `partitioningBy` vs `groupingBy(boolean)` is a contract difference, not a syntactic one. Every one of these is a legacy refactoring opportunity hiding in plain sight.

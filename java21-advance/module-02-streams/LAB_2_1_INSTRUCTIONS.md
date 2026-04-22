# Lab 2.1 — Higher-Order Functions & Closure Discipline

**Module:** 2 — Functional Architecture & Stream Internals  
**Files:** `Lab_2_1_FunctionalPipelines.java`  

---

## Activity 1: Function as First-Class Data

*The Illusion* — Most developers think of lambdas as shorthand for anonymous classes — a convenient syntax they plug into `.filter()` or `.map()`. They rarely assign a lambda to a variable, pass it to their own method, or return one from a factory. But `Function<T, R>` is an ordinary object with a type. You can store it, pass it, return it, and put it in a collection — just like a `String` or a `List`.

`Function<T, R>` lives in `java.util.function`. Its single abstract method is `.apply(T t)`, which takes one argument and returns a result. When you write `Function<String, String> trim = String::trim`, you're not writing shorthand — you're creating an object that encapsulates a transformation. Activities 2 and 3 build on this idea: once a function is data, you can compose functions together and store them in maps.

### Steps

1. Open `Lab_2_1_FunctionalPipelines.java`.

2. Read the scaffolding section at the top — note the `SAMPLE_INPUTS` list. These strings have leading/trailing whitespace and mixed casing by design.

3. Locate **TODO 1.1**. 

    Create a method called ac1_applyAndPrint that accepts a Function<String, String> and a String which applies the function to the string and prints the result in the format: `"[label] -> result"` where label is a third String parameter for display purposes.

4. Paste this snippet:

```java
private static void ac1_applyAndPrint(Function<String, String> fn, String input, String label) {
    String result = fn.apply(input);
    System.out.println("  [" + label + "] -> \"" + result + "\"");
}
```

5. Locate **TODO 1.2**. 
    Create two Function<String, String> fields: 
    
    `ac1_trim` to trims whitespace
    
    `ac1_upper` to converts to uppercase

6.Paste this snippet:
```java
private static final Function<String, String> ac1_trim = String::trim;
private static final Function<String, String> ac1_upper = String::toUpperCase;
```

7. Locate **TODO 1.3**.

8. Uncomment the `activity1()` method.

9. Scroll down to the `main()` method and locate the commented-out `case "1"` inside the switch.

10. Uncomment `case "1" -> activity1();`.

11. Compile and run `Lab_2_1_FunctionalPipelines`.

12. Select **1** from the menu.

13. Observe:
    - `ac1_trim` and `ac1_upper` are `Function` objects stored in variables — they're data, not syntax sugar.
    - `ac1_applyAndPrint` accepts *any* `Function<String, String>` — it doesn't know or care what the function does internally.
    - The same method handles both trimming and uppercasing because the *behaviour* was passed in as a parameter.
    - **Question:** If you wanted to add a third transformation (e.g., reverse the string), what would you need to change in `ac1_applyAndPrint`? Why?

---

## Activity 2: Composition — `andThen` vs `compose`

*The Illusion* — Developers see `.andThen()` and `.compose()` in autocomplete and assume they do the same thing. They don't. 

They reverse the execution order. The difference matters every time you chain more than one transformation, and guessing wrong means silent data corruption — the types match, the code compiles, but the output is wrong.

`Function<T, R>` provides two composition methods. 

`.andThen(g)` means "apply me first, then apply g to my result" — it reads left-to-right, which matches how most developers think. 

`.compose(g)` means "apply g first, then apply me to g's result" — it follows mathematical function composition notation, right-to-left. 

Both return a new `Function`, so you can keep chaining. The critical insight: `f.andThen(g)` and `g.compose(f)` produce the same result. Swap either side and the output changes.

### Steps

1. In `Lab_2_1_FunctionalPipelines.java`, read the three base functions provided above Activity 2: `trim`, `toUpper`, and `addBrackets`. These are your building blocks.

2. Locate **TODO 2.1**. Create two composed Function<String, String> fields:
 
    `ac2_trimThenUpper` that trims first, then uppercases (use andThen)
 
    `ac2_upperComposeTrim` does uppercases first, then trims (use compose on toUpper).  
  
    Think carefully: what does compose mean for execution order?

3. Paste this snippet:

```java
private static final Function<String, String> ac2_trimThenUpper =
    trim.andThen(toUpper);

private static final Function<String, String> ac2_upperComposeTrim =
    toUpper.compose(trim);
```

4. Locate **TODO 2.2**.

5. Uncomment the `ac2_threeStage` field.

6. Locate **TODO 2.3**.

7. Uncomment the `activity2()` method.

8. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

9. Compile and run. Select **2**.

10. Observe:
    - `trim.andThen(toUpper)` and `toUpper.compose(trim)` produce **identical** output. This is deliberate — they describe the same pipeline from different directions.
    - The three-stage pipeline (`trim → toUpper → addBrackets`) shows that composition scales. Each `.andThen()` adds a step, and the chain reads in execution order.
    - **Question:** What would happen if you wrote `toUpper.andThen(trim)` instead of `trim.andThen(toUpper)`? Would the output differ for these sample strings? Why or why not?

---

## Activity 3: Dynamic Strategy with Function Maps

*The Ambiguity* — The classic Strategy pattern works, but it scatters behaviour across an interface and N implementation classes. 

When someone asks "what strategies are available?", you grep the codebase and hope you find them all. A `Map<String, Function<T, R>>` makes the set of strategies explicit, runtime-inspectable, and hot-swappable — with no class hierarchy at all.

In traditional OO, a strategy is an interface with multiple concrete implementations selected at runtime via a factory or config. 

With `Function<T, R>`, each strategy is a single lambda or method reference. A `Map` keyed by name replaces the entire class tree. 

This isn't always the right trade. Strategies that carry complex internal state still benefit from full classes. But for stateless transformations (and most strategies *are* stateless), the functional version is dramatically simpler. 

Read the legacy strategy scaffolding in the comment block near the top of the file to see what you're replacing.

### Steps

1. Read the legacy Strategy pattern comment block in the scaffolding section. Note: one interface, three classes, a map to wire them up. Four source files in a real project.

2. Locate **TODO 3.1**. 

    Create a method called `ac3_buildStrategyMap` that:
    returns a `Map<String, Function<String, String>>`
    populates it with three entries:
    "shout"   -> converts to uppercase
    "whisper" -> converts to lowercase
    "redact"  -> replaces all vowels (aeiouAEIOU) with '*'

3. Paste this snippet:

```java
private static Map<String, Function<String, String>> ac3_buildStrategyMap() {
    Map<String, Function<String, String>> map = new HashMap<>();
    map.put("shout",   String::toUpperCase);
    map.put("whisper", String::toLowerCase);
    map.put("redact",  s -> s.replaceAll("[aeiouAEIOU]", "*"));
    return map;
}
```

4. Locate **TODO 3.2**.

    create a method called `ac3_applyStrategy` that:
    accepts the map, a strategy name (String), and an input (String)
    looks up the strategy by name
    if found, applies it and prints the result
    if not found, prints a warning: `"  Unknown strategy: <name>"`


```java
private static void ac3_applyStrategy(
        Map<String, Function<String, String>> strategies,
        String name,
        String input) {

    Function<String, String> strategy = strategies.get(name);
    if (strategy == null) {
        System.out.println("  Unknown strategy: " + name);
        return;
    }
    System.out.println("  [" + name + "] -> \"" + strategy.apply(input) + "\"");
}
```

5. Locate **TODO 3.3**.

6. Uncomment the `activity3()` method.

7. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

8. Compile and run. Select **3**.

9. Observe:
    - Three strategies, zero classes. Each strategy is a single lambda or method reference stored in a `Map`.
    - `strategies.keySet()` tells you exactly what's available — no grepping required.
    - The unknown strategy lookup prints a clean warning instead of a `NullPointerException`. This is defensive use of the `Map` API.
    - **Question:** How would you add a new strategy at runtime (e.g., from user input or a config file)? What does that tell you about the flexibility difference between this approach and the class-based version?

---

## Activity 4: Effectively Final — The Boundary

*The False Promise* — "Effectively final" sounds like Java is protecting you from mutation bugs inside lambdas. It partially does: you can't reassign a captured local variable. 

But if that variable points to a mutable object — a `List`, a `Map`, an `AtomicInteger` — you can mutate the object's *contents* freely. 

The compiler says nothing. This half-protection gives developers false confidence that their closures are safe.

The JLS requires that any local variable captured by a lambda must be *effectively final* — assigned exactly once and never reassigned within its scope. 

This prevents the lambda from seeing a reference that changes after capture. But "final reference" is not "immutable object." A `final List<String>` is still a mutable list — you can `.add()`, `.remove()`, and `.clear()` through it. 

The reference is locked; the state behind it is wide open. This distinction becomes critical in Activity 5, where mutation through a captured reference breaks under concurrency.

### Steps

1. Locate **TODO 4.1** inside the `activity4()` method.

2. Paste this snippet:

```java
        // Compiler error: local variable 'message' is not effectively final
           String message = "hello";
           SAMPLE_INPUTS.forEach(s -> {
               message = s;  // ← reassignment — compiler rejects this
           });
           System.out.println(message);
```

3. Notice the compiler error. It does not allow reaasigment.

4. Locate **TODO 4.2**.

5. Uncomment the `forEach` line that mutates `collected`.

6. Locate **TODO 4.3**.

7. Uncomment the print block that reveals the mutated list.

8. Compile and run. Select **4**.

9. Observe:
    - Part A: The compiler blocks reassignment of `message` — this is "effectively final" enforcement in action.
    - Part B: The compiler *allows* mutation of `collected` via `.add()` — the reference `collected` was never reassigned, so it's technically effectively final. But the list's contents changed.
    - The distinction: "effectively final" protects the **reference**, not the **state**. The compiler can verify that a variable is never reassigned. It cannot verify that the object behind that variable is never mutated.
    - **Question:** If effectively final only protects references, what mechanism would you need to prevent content mutation inside a lambda? Think about what Java provides (or doesn't) for true immutability.

---

## Activity 5: Illegal State Mutation in Closures

*The Trap* — A lambda inside a sequential stream mutates an external `ArrayList`. It works every time, reliably. 

So the developer moves to `.parallelStream()` for a performance boost. No compiler warning. No API contract violation. 

The code runs and silently produces corrupted results. Missing items, duplicate items, or an `ArrayIndexOutOfBoundsException` if you're lucky enough to get one.

Streams assume that lambdas are *stateless* and *non-interfering* — they don't read or write shared mutable state. 

Sequential streams happen to tolerate violations because there's only one thread executing the pipeline. Parallel streams shatter that assumption: the `ForkJoinPool` splits the source across multiple threads, and all of them call your lambda concurrently. 

`ArrayList` is not thread-safe — concurrent `.add()` calls corrupt its internal array. 

The fix is to never mutate external state inside a stream. Use `.collect()` instead. `Collectors.toList()` uses thread-local accumulators that merge safely.

### Steps

1. Locate **TODO 5.1** inside the `activity5()` method.
    Create an `ArrayList<String>` called `'resultA'`.
    Use `sourceData.stream().forEach()` to add each item uppercased into resultA.
    Then call: `checkIntegrity("Sequential forEach", sourceData, resultA);`

2. Paste this snippet:

```java
        List<String> resultA = new ArrayList<>();
        sourceData.stream().forEach(s -> resultA.add(s.toUpperCase()));
        checkIntegrity("Sequential forEach", sourceData, resultA);
```

3. Locate **TODO 5.2**.

4. Uncomment the parallel mutation block (`resultB`).

5. Locate **TODO 5.3**.

6. Uncomment the safe collect block (`resultC`) and re-comment back the parallel mutation block (`resultB`).

7. Compile and run. Select **5**.

8. Run Activity 5 **three or four times** — parallel corruption is non-deterministic. Some runs will show missing items, some will show duplicates, and some may throw `ArrayIndexOutOfBoundsException`.

9. Observe:
    - Sequential `forEach` + external mutation: passes integrity check every time. This is the false sense of security.
    - Parallel `forEach` + external mutation: fails unpredictably. Size mismatches, duplicate entries, or runtime exceptions — all from the same code, just run in parallel.
    - Parallel `collect`: passes integrity check every time. `Collectors.toList()` handles thread-safe accumulation internally.
    - **Question:** Why does sequential `forEach` mutation always produce correct results? What specific property of single-threaded execution makes it safe — and why is that property unreliable as a guarantee?

---

## Lab Complete

Every activity in this lab explored the same underlying truth: functions in Java are objects, and objects have rules. `Function<T, R>` gives you composition and strategy patterns with almost no ceremony — but only if you respect the boundaries. "Effectively final" looks like protection but only guards references. Sequential streams tolerate mutation but parallel streams don't. The discipline isn't optional — it's the difference between code that works in testing and code that survives production.

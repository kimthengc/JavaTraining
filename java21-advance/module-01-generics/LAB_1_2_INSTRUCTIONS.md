# Lab 1.2 — Advanced Generic Patterns

**Module:** 1 — Java Generics  
**Files:** `Lab_1_2_AdvancedPatterns.java`, `ReportBuilder.java`  

---

## Activity 1: Unbounded Wildcards

Java's type system has a counterintuitive rule: `List<String>` is **not** a subtype of `List<Object>`. This means a method that accepts `List<Object>` cannot receive a `List<String>` — even though `String` is a subtype of `Object`. The False Promise is that `List<Object>` looks universal but is actually very narrow.

The unbounded wildcard `List<?>` is the genuine universal — it accepts a list of any element type. The trade-off is intentional: you can read elements from a `List<?>` as `Object`, but you cannot write to it. The compiler closes the write door because it cannot know what type would be safe to insert.

### Steps

1. Open `Lab_1_2_AdvancedPatterns.java` 
2. Read the `ac1_unboundedWildcards()` method. Note the `ac1_printAllObjects()` observe its parameter type 
3. Uncomment the `List<String>` call and print statements
4. **Question:** Why does it not compile?
5. Paste this snippet into `TODO 1`
```java
static void ac1_printAll(List<?> items) {
    for (Object item : items) {
        System.out.println("  item: " + item + " (" + item.getClass().getSimpleName() + ")");
    }
}
```
6. Uncomment lines under `TODO 2`
7. Run `Lab_1_2_AdvancedPatterns` and select **1**.

8. Observe:
   - Three lists — `String`, `Integer`, `Double` — all print through a single method with no cast.
   - Try changing the parameter back to `List<Object>` — which of the three call sites breaks first?
   - Try adding a write inside the loop, e.g. `items.add(item)` — what does the compiler say and why?
   - **Question:** `List<String>` is rejected by `List<Object>` but accepted by `List<?>`. What rule explains the difference, and what does the compiler give up in exchange for that flexibility?

---

## Activity 2: Self-Bounded Comparable

`Comparable` has been in Java since 1.2. Most developers have used it without thinking about its type parameter. 

The raw form — `Comparable` without `<T>` — compiles and runs happily until you mix types. There is no compile-time guard: the error lands at runtime as a `ClassCastException`.

`T extends Comparable<T>` tightens the contract precisely. It does not just say "T is comparable" — it says "T compares to **itself**". 

A `String` compares to `String`. An `Integer` compares to `Integer`. The moment you try to mix them, the compiler rejects the call site rather than waiting for the runtime to crash.

### Steps
1. Read `ac2_selfBoundedComparable()`. 
2. Note `ac2_findMaxRaw()` the raw version that trusts the caller completely. 
3. Locate at `TODO 3` and uncomment the mixed-list lines and run activity 2 to see the `ClassCastException` before you fix anything. Read the message carefully — note which types collided.

4. Locate `TODO 4`. 

5. Paste this snippet.

```java
static <T extends Comparable<T>> T ac2_findMax(List<T> items) {
    T max = items.get(0);
    for (T item : items) {
        if (item.compareTo(max) > 0) {
            max = item;
        }
    }
    return max;
}
```

6. Locate `TODO 5` and uncomment the codes.

5. Run `Lab_1_2_AdvancedPatterns` and select **2**.

5. Observe:
   - `findMax` returns the correct type — no cast needed at the call site.
   - Both `List<String>` and `List<Integer>` work through the same method.
   - Re-comment the mixed-list lines, then try passing a mixed list to `ac2_findMax` — where does the error appear now?
   - **Question:** The raw `Comparable` already has `compareTo()` — so what does the `<T>` in `Comparable<T>` add that raw `Comparable` cannot provide?

---

## Activity 3: Fluent Builder Hierarchy

A fluent builder lets you construct an object in a single chained expression. 

Each method returns something you can immediately call the next method on. The pattern breaks the moment inheritance enters. A base class method that returns `this` hands back the base type, not the subclass type. Methods defined only on the subclass disappear from the chain.

The fix is a type parameter on the base class: `BaseBuilder<T extends BaseBuilder<T>>`. The base class does not know what it will become — but it delegates that knowledge to whoever extends it. 

Each subclass declares itself as `T`, so the base methods return the subclass type rather than the base type. The chain stays intact without any cast at the call site.

### Steps
1. Open `ReportBuilder.java`. 
2. Read the `BrokenBaseBuilder` and `BrokenPdfReportBuilder` classes first 
3. Note that `title()` and `author()` return `BrokenBaseBuilder`, which is why the fluent chain in the runner had to be abandoned for a variable-based workaround.

4. Find `TODO 3.1`
5. Paste this snippet below

```java
abstract class BaseBuilder<T extends BaseBuilder<T>> {

    protected String title  = "";
    protected String author = "";

    // TODO 3.2 — add title and author methods that returns type parameter
}
```
6. Locate `TODO 3.2`
7. Paste this snippet below

```java
    @SuppressWarnings("unchecked")
    public T title(String title) {
        this.title = title;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T author(String author) {
        this.author = author;
        return (T) this;
    }
```
8. Locate `TODO 3.3` and uncomment the `PdfReportBuilder` class.
9. Return to the `Lab_1_2_AdvancedPatterns` and Uncomment the code at `TODO 3.4`

10. Run `Lab_1_2_AdvancedPatterns` and select **3**.

11. Observe:
   - The workaround block builds a `Report` using three separate statements and a variable.
   - The fixed block builds the same `Report` in a single fluent chain — `title()`, `author()`, `landscape()`, `build()` — no intermediate variable, no cast.
   - Uncomment the broken chain block in `ac3_fluentBuilder()` — read the compile error and identify exactly which return type is wrong.
   - **Question:** The fix uses `(T) this` — an unchecked cast. The compiler warns about it, which is why `@SuppressWarnings("unchecked")` is there. Why is the cast unavoidable here, and why is it guaranteed safe at runtime even though the compiler cannot verify it?

---

## Lab Complete

You have seen three distinct uses of the same underlying idea — the type parameter as a precision tool:

- `List<?>` — a wildcard that opens a method to any typed list while the compiler closes the write door.
- `T extends Comparable<T>` — a self-bound that enforces T compares to itself, not to arbitrary objects.
- `T extends BaseBuilder<T>` — a self-bound that lets a base class return the correct subclass type through a chain.

The syntax looks similar in all three cases. The problem being solved is different each time.

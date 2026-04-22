# Lab 1.1: Variance & PECS. The Type System Working For You

Why does the compiler reject `List<Integer>` where a `List<Number>` is expected — even though `Integer` clearly *is* a `Number`? And why does removing the type parameter "fix" it, only to blow up at runtime instead?

In this lab you will follow a **Before → Pain → After** narrative through five activities. You will watch raw types cause a `ClassCastException` at runtime, feel the rigidity of invariance at compile time, and then resolve both problems using wildcards. By Activity 5, the PECS principle — Producer Extends, Consumer Super — will not be a rule you memorised. It will be a conclusion you derived yourself.

**Domain:** A stock price processing pipeline.
**File:** `module-01-generics/lab/src/Lab_1_1_Variance.java`
**Duration:** Approximately 30–40 minutes.

---

## Before You Start

Open `Lab_1_1_Variance.java` and take 60 seconds to scan the file. You will see five `acX_` methods called sequentially from `main`. Each activity maps to one method. Work top to bottom — do not skip activities.

---

## Activity 1: The Raw Type Trap

A **raw type** is a generic class used without its type parameter. ie. `List` instead of `List<String>`. This was common before Java 5. The compiler accepts it for backwards compatibility. The JVM does not protect you at runtime.

The danger: the bug is introduced in one place, the crash happens somewhere else entirely. In production, these two points can be in different classes, different threads, or hours apart.

### Steps

1. Locate `ac1_rawTypeChaos()`. The scaffolding has already loaded a raw `List` with two `String` trade IDs and one `Integer` that has sneaked in.

2. The `TODO` asks you to iterate the raw list and cast each element to `String` for processing.

3. Paste this snippet inside the `for` loop, replacing `// TODO`:
   
   ```java
   for (int i = 0; i < pipeline.size(); i++) {
       String trade = (String) pipeline.get(i);
       System.out.println("  Processing: " + trade);
   }
   ```

4. Run `Lab_1_1_Variance.java`.

5. Observe:
   
   - Trades 0 and 1 process cleanly — the `String` casts succeed without complaint.
   - Trade 2 throws `ClassCastException: Integer cannot be cast to String`.
   - The `Integer` was added three lines above the loop. The crash happens inside the loop.
   - The compiler raised no warning anywhere in this file.
   - **Question:** The bug and the crash are in different places. In a large codebase, what does that mean for how long it takes to find the root cause?

---

## Activity 2: The Invariance Wall

We add a type parameter to fix the raw type problem. `List<Number>` should accept `List<Integer>`, right? `Integer` is a `Number`. The method only reads — it cannot corrupt anything.

The compiler disagrees. This is **invariance**: `List<Integer>` is not a subtype of `List<Number>`, even though `Integer` is a subtype of `Number`. Generic types in Java do not inherit the subtype relationship of their type parameters.

The reason is sound. If `List<Integer>` were treated as a `List<Number>`, you could legally write a `Double` into it through the `Number` reference — silently corrupting a list that was supposed to hold only integers. The compiler blocks the assignment entirely to close that gap.

### Steps

1. Locate `ac2_invarianceWall()`. The scaffolding has created a `List<Integer>` and a `List<Double>`, each loaded with sample prices.

2. There is no `TODO` here. Instead, find these two commented-out lines:
   
   ```java
   //   printPrices(integerPrices);   // ← COMPILE ERROR
   //   printPrices(doublePrices);    // ← COMPILE ERROR
   ```

3. Uncomment **one** of them and save the file. Read the error the compiler produces:
   
   ```
   incompatible types: List<Integer> cannot be converted to List<Number>
   ```

4. Re-comment the line before continuing to Activity 3.

5. Observe:
   
   - `Integer` IS-A `Number`. `List<Integer>` IS-NOT a `List<Number>`.
   - `printPrices()` is a read-only method. It cannot add, remove, or modify elements. Yet the compiler still refuses the call.
   - **Question:** The compiler's refusal feels unfair here — the method is read-only and cannot cause harm. What would need to change in the method signature to make the compiler accept both list types?

---

## Activity 3: Covariance — `? extends` (Producer)

We widen the method signature using an **upper-bounded wildcard**: `? extends Number`.

This reads as: *"a List of some unknown type, which is Number or a subtype of Number."* The compiler now accepts `List<Integer>`, `List<Double>`, `List<BigDecimal>` — any numeric subtype.

The trade-off is deliberate: because the compiler does not know the exact subtype at the call site, it cannot safely allow writes. You cannot call `add()` on a `? extends` list. The list is a **Producer** — it produces values for you to read, nothing more.

### Steps

1. Locate `ac3_covarianceExtends()`. The scaffolding has the same `List<Integer>` and `List<Double>` from Activity 2. Before continuing, find the method `printPricesWildcard()` below in the file and read its signature — notice the single token that changed from `printPrices()`.

2. The `TODO 3.1` asks you to call `printPricesWildcard()` with both lists.

3. Paste this snippet, replacing `// TODO 3.1`:
   
   ```java
   System.out.println("Integer prices:");
   printPricesWildcard(integerPrices);
   
   System.out.println("Double prices:");
   printPricesWildcard(doublePrices);
   ```

4. Paste this snippet, replacing `// TODO 3.2`:
   
   ```java
   static void printPricesWildcard(List<? extends Number> prices) {
       for (Number price : prices) {
           System.out.println("  Price: " + price);
       }
   
       //prices.add(999);
   }
   ```

5. Run `Lab_1_1_Variance.java`.

6. Observe:
   
   - Both `List<Integer>` and `List<Double>` are now accepted. The invariance wall is gone.
   - Inside `printPricesWildcard()`, try adding `prices.add(999);` — the compiler refuses.
   - The method signature changed by one token. The behaviour at runtime is identical. The flexibility at the call site is completely different.
   - **Question:** The compiler allows reads but blocks writes on a `? extends` list. You know the list contains `Number` values — so why can't you write a `Number` into it?

---

## Activity 4: Contravariance — `? super` (Consumer)

Now the other direction. We want to **write** `Integer` values into a list. The destination list should be flexible — `List<Integer>`, `List<Number>`, and `List<Object>` should all be valid targets, because all three can legally hold an `Integer`.

A **lower-bounded wildcard** `? super Integer` does exactly this. It reads as: *"a List of some unknown type, which is Integer or a supertype of Integer."*

The trade-off mirrors Activity 3: because the compiler only knows the list holds "some supertype of Integer", it cannot safely give you anything more specific than `Object` when you read back. The list is a **Consumer** — it consumes values you push into it.

### Steps

1. Locate `ac4_contravarianceSuper()`. The scaffolding has created a `List<Number>` and a `List<Object>`, both empty. Before continuing, find the method `collectTrades()` below in the file and read its signature and body.

2. The `TODO 4.1` asks you to call `collectTrades()` with both buckets and print the results.

3. Paste this snippet, replacing `// TODO 4.1`:
   
   ```java
   collectTrades(numberBucket);
   collectTrades(objectBucket);
   
   System.out.println("Number bucket: " + numberBucket);
   System.out.println("Object bucket: " + objectBucket);
   ```

4. Paste this snippet, replacing `// TODO 4.2`:
   
   ```java
   static void collectTrades(List<? super Integer> bucket) {
       bucket.add(100);
       bucket.add(200);
       bucket.add(300);
       // Try: 
       // Integer val = bucket.get(0);
       // Try: 
       // Object val = bucket.get(0); — this compiles. That is the only safe contract.
   }
   ```

5. Run `Lab_1_1_Variance.java`.

6. Observe:
   
   - `List<Number>` and `List<Object>` are both accepted as valid destinations for `Integer` values.
   - Inside `collectTrades()`, try `Integer val = bucket.get(0);` — the compiler refuses. Try `Object val = bucket.get(0);` — that compiles.
   - `? super` is the mirror of `? extends`: one opens up writes, the other opens up reads, and each closes the operation it cannot safely guarantee.
   - **Question:** You can write `Integer` values in, but reading back gives you only `Object`. What does that tell you about what the compiler knows — and does not know — about the list's actual type at this point?

---

## Activity 5: PECS Unified — Producer Extends, Consumer Super

Activities 3 and 4 were the two halves of a single principle. Now we combine them.

A `copy()` method transfers elements from a source list into a destination list. We want maximum flexibility on both sides:

- `src` — we only **read** from it → it is a Producer → `? extends T`
- `dest` — we only **write** into it → it is a Consumer → `? super T`

This is **PECS**: *Producer Extends, Consumer Super.*

It is not a rule to memorise. It is the natural consequence of asking two questions:

1. Am I reading from this structure? → `? extends`
2. Am I writing into this structure? → `? super`

### Steps

1. Locate `ac5_pecsUnified()`. The scaffolding has a `List<Integer>` as the source and a `List<Number>` as the destination. Before continuing, find the `copy()` method below in the file and read both wildcards in its signature — you have seen each one before, in Activities 3 and 4.

2. The `TODO 5.1` asks you to call `copy()` and print the destination.

3. Paste this snippet, replacing `// TODO 5.1`:
   
   ```java
   copy(source, destination);
   System.out.println("Copied to destination: " + destination);
   ```

4. Paste this snippet, replacing `// TODO 5.2`:
   
   ```java
   static <T> void copy(List<? extends T> src, List<? super T> dest) {
       for (T element : src) {
           dest.add(element);
       }
   }
   ```

5. Run `Lab_1_1_Variance.java`.

6. Observe:
   
   - `List<Integer>` is accepted as `src` — it satisfies `? extends T` where `T` is `Integer`. ✓
   - `List<Number>` is accepted as `dest` — it satisfies `? super T` where `T` is `Integer`. ✓
   - Try swapping the arguments — pass `destination` as `src` and `source` as `dest`. The compiler rejects it.
   - Try `copy(source, new ArrayList<Object>())` — it works. `Object` is a valid consumer of `Integer`.
   - **Question:** The `copy()` method works across a range of type combinations without casting, without `@SuppressWarnings`, and without runtime risk. What would the equivalent legacy raw-type implementation have required to achieve the same flexibility?

---

## Summary

| Concept        | What it means                                       | Wildcard      |
| -------------- | --------------------------------------------------- | ------------- |
| Raw type       | No type parameter — compiler silent, runtime unsafe | none          |
| Invariance     | `List<Sub>` is not a `List<Super>`                  | none          |
| Covariance     | Read from a flexible source safely                  | `? extends T` |
| Contravariance | Write into a flexible sink safely                   | `? super T`   |
| PECS           | Producer Extends, Consumer Super                    | both          |

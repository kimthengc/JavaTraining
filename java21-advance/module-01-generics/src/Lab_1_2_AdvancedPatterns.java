import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

// ============================================================
//  Lab 1.2 — Advanced Generic Patterns
//
//  Activities
//    1 — Unbounded Wildcards   (List<?>)
//    2 — Self-Bounded Comparable  (T extends Comparable<T>)
//    3 — Fluent Builder Hierarchy (T extends BaseBuilder<T>)
//
// ============================================================

public class Lab_1_2_AdvancedPatterns {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("===========================================");
        System.out.println("  Lab 1.2 — Advanced Generic Patterns");
        System.out.println("===========================================");
        System.out.println("  1. Unbounded Wildcards");
        System.out.println("  2. Self-Bounded Comparable");
        System.out.println("  3. Fluent Builder Hierarchy");
        System.out.println("-------------------------------------------");
        System.out.print("Select activity: ");

        int choice = scanner.nextInt();
        System.out.println();

        switch (choice) {
            case 1 -> ac1_unboundedWildcards();
            case 2 -> ac2_selfBoundedComparable();
            case 3 -> ac3_fluentBuilder();
            default -> System.out.println("Invalid selection.");
        }

        scanner.close();
    }

    // =========================================================
    //  ACTIVITY 1 — Unbounded Wildcards
    // =========================================================

    static void ac1_unboundedWildcards() {
        System.out.println(">>> Activity 1: Unbounded Wildcards");
        System.out.println();

        // --- THE FALSE PROMISE ---
        // List<Object> claims to be a universal container.
        // It compiles. It accepts anything. It tells you nothing.

        System.out.println("-- The False Promise: List<Object> --");
        List<Object> mixed = Arrays.asList("hello", 42, 3.14, true);
        ac1_printAllObjects(mixed);

        
        // List<String> strings = List.of("a", "b");
        // ac1_printAllObjects(strings); // <-- does not compile

        System.out.println();

        // --- THE FIX ---
        // List<?> is a true universal container. It compiles. It accepts anything.
        System.out.println("-- The Fix: List<?> --");
        List<String>  strings  = Arrays.asList("alpha", "beta", "gamma");
        List<Integer> integers = Arrays.asList(10, 20, 30);
        List<Double>  doubles  = Arrays.asList(1.1, 2.2, 3.3);

        // TODO2 — uncomment the calls to ac1_printAll below once you implement it to accept List<?>
        ac1_printAll(strings);
        ac1_printAll(integers);
        ac1_printAll(doubles);

        System.out.println();
    }

    // TODO1 — create a new method ac1_printAll that accepts List<?> and prints each item
    static void ac1_printAll(List<?> items) {
        for (Object item : items) {
            System.out.println("  item: " + item + item.getClass().getSimpleName());
        }
    }
    
    // The False Promise — accepts List<Object> only, not List<String>
    static void ac1_printAllObjects(List<Object> items) {
        for (Object item : items) {
            System.out.println("  item: " + item);
        }
    }

       

    // =========================================================
    //  ACTIVITY 2 — Self-Bounded Comparable
    // =========================================================

    static void ac2_selfBoundedComparable() {
        System.out.println(">>> Activity 2: Self-Bounded Comparable");
        System.out.println();

        // --- THE ILLUSION ---
        // Raw Comparable compiles fine. The compiler has no idea what
        // you are comparing to what. Mix types and the runtime explodes.
        
        System.out.println("-- The Illusion: raw Comparable --");
        List<Comparable> dangerous = Arrays.asList("banana", "apple", "cherry");
        // System.out.println("  findMaxRaw on strings: " + ac2_findMaxRaw(dangerous));

        // TODO 3: Uncomment these two lines to see the ClassCastException at runtime:
        List<Comparable> mixed = Arrays.asList("banana", 42, "cherry");
        // System.out.println("Boom" + ac2_findMaxRaw(mixed)); // BOOM
        //  System.out.println("Boom" + ac2_findMaxRaw2(mixed)); // BOOM

        System.out.println();

        // --- THE FIX ---
        // T extends Comparable<T> enforces that T compares to itself.
        // Mixed types are rejected at the call site — before runtime.
        System.out.println("-- The Fix: T extends Comparable<T> --");
        List<String>  names  = Arrays.asList("banana", "apple", "cherry");
        List<Integer> scores = Arrays.asList(42, 7, 99, 3);

        // TODO 5 — uncomment the calls to ac2_findMax below once you implement it to accept List<T extends Comparable<T>>
        System.out.println("  findMax strings: " + ac2_findMaxRaw2(names));
        System.out.println("  findMax integers: " + ac2_findMaxRaw2(scores));

        System.out.println();
    }

    // TODO 4 — create a new method ac2_findMax that accepts List<T extends Comparable<T>> and returns the max item

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Comparable ac2_findMaxRaw(List<Comparable> items) {
        Comparable max = items.get(0);
        for (Comparable item : items) {
            if (item.compareTo(max) > 0) {
                max = item;
            }
        }
        return max;
    }

    static <T extends Comparable<T>> T ac2_findMaxRaw2(List<T> items) {
        T max = items.get(0);
        for (T item : items) {
            if (item.compareTo(max) > 0) {
                max = item;
            }
        }
        return max;
    }

         

    // =========================================================
    //  ACTIVITY 3 — Fluent Builder Hierarchy
    // =========================================================

    static void ac3_fluentBuilder() {
        System.out.println(">>> Activity 3: Fluent Builder Hierarchy");
        System.out.println();

        // --- THE CRACK ---
        // BrokenBaseBuilder.title() returns BrokenBaseBuilder — not BrokenPdfReportBuilder.
        // The subclass-specific method landscape() vanishes after any base call.
        System.out.println("-- The Crack: broken builder chain --");

        // Uncomment the block below to see the compile error:
        //   Report broken = new BrokenPdfReportBuilder()
        //       .title("Q4 Results")     // returns BrokenBaseBuilder
        //       .landscape()             // ERROR — BrokenBaseBuilder has no landscape()
        //       .build();

        // The only workaround is an ugly cast — which defeats the purpose of a typed API
        BrokenPdfReportBuilder b = new BrokenPdfReportBuilder();
        b.title("Q4 Results");
        b.author("Alice");
        b.landscape();
        Report broken = b.build();
        System.out.println("  Built (broken chain workaround): " + broken);

        System.out.println();

        // --- THE FIX ---
        // With the type parameter in place, title() and author() return T,
        // which resolves to PdfReportBuilder — landscape() stays in the chain.
        System.out.println("-- The Fix: type parameter on BaseBuilder --");

        // Once TODO 3.1 and 3.2 are complete, this fluent chain compiles cleanly:
        // TODO 3.4 — uncomment the block below once you implement the fixed builder hierarchy
        Report fixed = new PdfReportBuilder()
                .title("Q4 Results")
                .author("Alice")
                .landscape()
                .build();
        System.out.println("  Built (fluent chain): " + fixed);

        System.out.println();
        
    }

}

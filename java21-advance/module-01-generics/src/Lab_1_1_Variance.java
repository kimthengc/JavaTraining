import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Lab_1_1_Variance {

    public static void main(String[] args) {

        System.out.println("=================================================");
        System.out.println(" Lab 1.1 — Variance & PECS");
        System.out.println("=================================================\n");
        System.out.println("  1. Raw Type Chaos");
        System.out.println("  2. The Invariance Wall");
        System.out.println("  3. Covariance — ? extends (Producer)");
        System.out.println("  4. Contravariance — ? super (Consumer)");
        System.out.println("  5. PECS Unified");
        System.out.println();

        int choice = 0;

        try (Scanner scanner = new Scanner(System.in)) {
            while (choice < 1 || choice > 5) {
                System.out.print("Enter activity number (1-5): ");
                if (scanner.hasNextInt()) {
                    choice = scanner.nextInt();
                    if (choice < 1 || choice > 5) {
                        System.out.println("Invalid input. Please enter a number between 1 and 5.");
                    }
                } else {
                    System.out.println("Invalid input. Please enter a number between 1 and 5.");
                    scanner.next();
                }
            }
        }

        System.out.println();

        switch (choice) {
            case 1 -> ac1_rawTypeChaos();
            case 2 -> ac2_invarianceWall();
            case 3 -> ac3_covarianceExtends();
            case 4 -> ac4_contravarianceSuper();
            case 5 -> ac5_pecsUnified();
        }
    }




    
    // =========================================================================
    // ACTIVITY 1 — The Raw Type Trap
    //
    // This is what legacy code looks like. No generics, no type parameter.
    // The compiler lets it through without complaint.
    // The JVM disagrees at runtime.
    // =========================================================================
    static void ac1_rawTypeChaos() {
        System.out.println("--- ACTIVITY 1: Raw Type Chaos ---");
        System.out.println("OBSERVE: The compiler is silent. No warnings, no errors.");
        System.out.println("OBSERVE: The JVM is not silent.\n");

        // Raw type — no type parameter. Legal Java. Dangerous Java.
        // You will see this in any pre-Java-5 codebase.
        List pipeline = new ArrayList();
        pipeline.add("TRADE-001");   // String in
        pipeline.add("TRADE-002");   // String in
        pipeline.add(9999);          // Integer sneaks in — compiler says nothing

        System.out.println("Pipeline loaded. Processing trades...\n");

        // TODO: Iterate the raw List and cast each element to String
        for (Object object : pipeline) {
            String objString = (String) object;
            System.out.println("Processing" + objString);
        }
    
    }




    // =========================================================================
    // ACTIVITY 2 — The Invariance Wall
    //
    // We fix the raw type by adding a type parameter.
    // But now we hit Java's invariance rule head-on.
    //
    // List<Integer> IS-NOT a List<Number> — even though Integer IS-A Number.
    // This method signature is too rigid. It rejects perfectly valid input.
    // We will fix it in Activity 3 by introducing wildcards.
    //
    // =========================================================================
    static void ac2_invarianceWall() {
        System.out.println("\n--- ACTIVITY 2: The Invariance Wall ---");

        List<Integer> integerPrices = new ArrayList<>();
        integerPrices.add(150);
        integerPrices.add(300);
        integerPrices.add(750);

        List<Double> doublePrices = new ArrayList<>();
        doublePrices.add(150.50);
        doublePrices.add(300.75);

        // printPrices(integerPrices);   // ← COMPILE ERROR
        // printPrices(doublePrices);    // ← COMPILE ERROR

        System.out.println("OBSERVE: printPrices(integerPrices) would not compile.");
        System.out.println("OBSERVE: Integer IS-A Number. List<Integer> IS-NOT a List<Number>.");
        System.out.println("OBSERVE: This is invariance. We fix it in Activity 3.\n");
    }

   
    static void printPrices(List<Number> prices) {
        for (Number price : prices) {
            System.out.println("  Price: " + price);
        }
    }

    // =========================================================================
    // ACTIVITY 3 — Covariance: ? extends (Producer)
    //
    // We widen the method signature using an upper-bounded wildcard.
    // ? extends Number means: "any List whose elements are Number or a subtype."
    //
    // The trade-off: you can READ from it safely. You cannot WRITE to it.
    // The compiler enforces this. Try adding an element — it will refuse.
    // =========================================================================
    static void ac3_covarianceExtends() {
        System.out.println("--- ACTIVITY 3: Covariance — ? extends (Producer) ---");

        List<Integer> integerPrices = new ArrayList<>();
        integerPrices.add(150);
        integerPrices.add(300);
        integerPrices.add(750);

        List<Double> doublePrices = new ArrayList<>();
        doublePrices.add(150.50);
        doublePrices.add(300.75);

        // TODO 3.1: Call printPricesWildcard() with both integerPrices and doublePrices
        System.out.println("printPricesWildcard integer");
        printPricesWildcard(integerPrices);

        System.out.println("printPricesWildcard double");
        printPricesWildcard(doublePrices);
        
    }

    // TODO 3.2: Declare printPricesWildcard() static method with List<? extends Number> parameter 
    static void printPricesWildcard(List<? extends Number> prices) {
        for (Number price : prices) {
            System.out.println("price" + price);
        }

        // during compile time do not know what have been write
        // prices.add(999);
    }



    // =========================================================================
    // ACTIVITY 4 — Contravariance: ? super (Consumer)
    //
    // Now the other direction. We want to WRITE values into a list.
    // ? super Integer means: "any List that can legally hold an Integer."
    // That includes List<Integer>, List<Number>, List<Object>.
    //
    // The trade-off: you can WRITE safely. Reading gives you only Object.
    // =========================================================================
    static void ac4_contravarianceSuper() {
        System.out.println("\n--- ACTIVITY 4: Contravariance — ? super (Consumer) ---");

        List<Number> numberBucket = new ArrayList<>();
        List<Object> objectBucket = new ArrayList<>();

        // TODO 4.1: Call collectTrades() with both numberBucket and objectBucket
        collectTrades(numberBucket);
        collectTrades(objectBucket);
    }

    
    // TODO 4.2: Declare collectTrades() static method with List<? super Integer> parameter
    static void collectTrades(List<? super Integer> bucket) {
        bucket.add(200);
        bucket.add(1);

        // Integer i = bucket.get(0); // cannot do this, because it can be number or object

        Object obj = bucket.get(0);
        System.out.println("obj: " + obj);


    }



    // =========================================================================
    // ACTIVITY 5 — PECS Unified: Producer Extends, Consumer Super
    //
    // We combine both wildcards into one method.
    // src is a Producer — we read from it      → ? extends T
    // dest is a Consumer — we write into it    → ? super T
    //
    // This is the PECS principle. It is not a rule to memorise.
    // It is the natural conclusion of Activitys 3 and 4 working together.
    // =========================================================================
    static void ac5_pecsUnified() {
        System.out.println("\n--- ACTIVITY 5: PECS Unified ---");

        // Source: a specific list of Integer prices
        List<Integer> source = new ArrayList<>();
        source.add(150);
        source.add(300);
        source.add(750);

        // Destination: a wider list that can hold Number (or Object)
        List<Number> destination = new ArrayList<>();
        copy(source, destination);
        // TODO 5.1: Call the pecs copy method, then print the destination

    }

    
    // TODO 5.2: Declare the copy() static method with appropriate wildcard parameters
    static <T> void copy(List<? extends T> src, List<? super T> dest) {
        for (T object : src) {
            dest.add(object);
        }
    }

}

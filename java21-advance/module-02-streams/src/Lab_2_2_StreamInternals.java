import java.util.*;
import java.util.stream.*;

import static java.util.stream.Collectors.*;

/**
 * ─────────────────────────────────────────────────────────────────────────
 *  LEGACY SCAFFOLDING — read, do not paste
 * ─────────────────────────────────────────────────────────────────────────
 *  Activity 3 replaces the nested-loop style shown below. This is what
 *  "group orders by region then by category" looks like without collectors.
 *  It works. It compiles. It is also what most legacy Java codebases are
 *  full of.
 *
 *      Map<String, Map<String, List<Order>>> byRegionThenCategory = new HashMap<>();
 *      for (Order o : Order.SAMPLE) {
 *          byRegionThenCategory
 *              .computeIfAbsent(o.region(), k -> new HashMap<>())
 *              .computeIfAbsent(o.category(), k -> new ArrayList<>())
 *              .add(o);
 *      }
 *
 *  Count the moving parts: two map lookups, two lazy initialisations,
 *  one list append. The intent ("group by region then category") is
 *  buried under the mechanics. Activity 3 collapses this to a single
 *  expression.
 * ─────────────────────────────────────────────────────────────────────────
 */

@SuppressWarnings("unused")
public class Lab_2_2_StreamInternals {

    // ─────────────────────────────────────────────────────────────────────
    //  ACTIVITY 1 — Laziness Is Real
    // ─────────────────────────────────────────────────────────────────────

    // TODO 1.1 — create a method ac1_pipelineWithoutTerminal
    private static void ac1_pipelineWithoutTerminal() {
        Order.SAMPLE.stream()
                .peek(o -> System.out.println("    filter peek: " + o.customer()))
                .filter(o -> o.amount() > 500)
                .peek(o -> System.out.println("    map peek:    " + o.customer()))
                .map(Order::customer);
        // Notice: no terminal op. Nothing should run.
    }

    // TODO 1.2 — create a method ac1_pipelineWithTerminal
    private static void ac1_pipelineWithTerminal() {
        List<String> result = Order.SAMPLE.stream()
                .peek(o -> System.out.println("    filter peek: " + o.customer()))
                .filter(o -> o.amount() > 500)
                .peek(o -> System.out.println("    map peek:    " + o.customer()))
                .map(Order::customer)
                .collect(Collectors.toList());
        System.out.println("  result: " + result);
    }

    private static void activity1() {
        System.out.println("=== Activity 1: Laziness Is Real ===\n");

        System.out.println("-- Part A: pipeline with NO terminal op --");
        // TODO 1.3 — uncomment the call below AFTER implementing ac1_pipelineWithoutTerminal
        ac1_pipelineWithoutTerminal();
        System.out.println("(if no peek output appeared above, the pipeline never ran)\n");

        System.out.println("-- Part B: same pipeline WITH terminal op --");
        // TODO 1.3 — uncomment the call below AFTER implementing ac1_pipelineWithTerminal
        ac1_pipelineWithTerminal();
    }

//---------------------------------------------------------------------------------------------

    // ─────────────────────────────────────────────────────────────────────
    //  ACTIVITY 2 — Short-Circuiting on an Infinite Stream
    // ─────────────────────────────────────────────────────────────────────

    // TODO 2.1 — create a method ac2_infiniteShortCircuit
    private static void ac2_infiniteShortCircuit() {
        int[] pulled = { 0 }; // capture-safe counter (see Lab 2.1, Activity 4)

        Optional<Integer> first = Stream.iterate(1, n -> n + 1)
                .peek(n -> {
                    pulled[0]++;
                    System.out.println("    pulled: " + n);
                })
                .filter(n -> n % 7 == 0)
                .findFirst();

        System.out.println("  first multiple of 7: " + first.orElseThrow());
        System.out.println("  elements pulled from infinite stream: " + pulled[0]);
    }

    // TODO 2.2 — create a method ac2_boundedFullPull
   private static void ac2_boundedFullPull() {
        long count = IntStream.rangeClosed(1, 20).boxed()
                .filter(n -> n > 0)   // forces the terminal to actually walk elements
                .peek(n -> System.out.println("    visited: " + n))
                .count();

        System.out.println("  count: " + count);
    }

    private static void activity2() {
        System.out.println("=== Activity 2: Short-Circuiting on an Infinite Stream ===\n");

        System.out.println("-- Part A: infinite stream + findFirst --");
        // TODO 2.3 — uncomment the call below AFTER implementing ac2_infiniteShortCircuit
        ac2_infiniteShortCircuit();

        System.out.println("\n-- Part B: bounded stream + count --");
        // TODO 2.3 — uncomment the call below AFTER implementing ac2_boundedFullPull
        ac2_boundedFullPull();
    }

//---------------------------------------------------------------------------------------------

    // ─────────────────────────────────────────────────────────────────────
    //  ACTIVITY 3 — Multi-Level Grouping
    // ─────────────────────────────────────────────────────────────────────
    //  (Read the LEGACY SCAFFOLDING block at the top of this file before
    //  starting — Activity 3 is the collector-based replacement for it.)

    private static void activity3() {
        System.out.println("=== Activity 3: Multi-Level Grouping ===\n");

        System.out.println("-- Part A: group by region, then by category --");
        // TODO 3.1 — paste the two-level groupingBy expression below,
        //            assign to a variable, and print it with ac3_print(...)
        Map<String, Map<String, List<Order>>> byRegionThenCategory = Order.SAMPLE.stream()
                .collect(Collectors.groupingBy(Order::region,
                        Collectors.groupingBy(Order::category)));
        ac3_print("region -> category -> orders", byRegionThenCategory);

        System.out.println("\n-- Part B: group by region, sum amounts per region --");
        // TODO 3.2 — paste the groupingBy(region, summingDouble) expression,
        //            assign to a variable, and print it with ac3_print(...)
        Map<String, Double> totalByRegion = Order.SAMPLE.stream()
                .collect(groupingBy(Order::region,
                        summingDouble(Order::amount)));
        ac3_print("region -> total amount", totalByRegion);


        System.out.println("\n-- Part C: region -> category -> count --");
        // TODO 3.3 — paste the three-level nested collector expression,
        //            assign to a variable, and print it with ac3_print(...)
        Map<String, Map<String, Long>> countByRegionAndCategory = Order.SAMPLE.stream()
                .collect(groupingBy(Order::region,
                        groupingBy(Order::category,
                                counting())));
        ac3_print("region -> category -> count", countByRegionAndCategory);

    }

    /** Pretty-printer used by Activity 3 — keeps the console readable. */
    private static void ac3_print(String label, Map<?, ?> map) {
        System.out.println("  " + label + ":");
        map.forEach((k, v) -> System.out.println("    " + k + " -> " + v));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ACTIVITY 4 — Partitioning + Custom Downstream
    // ─────────────────────────────────────────────────────────────────────

    /** Small helper: a list of only low-value orders, used to expose the
     *  partitioningBy vs groupingBy(boolean) semantic difference. */
    private static final List<Order> LOW_VALUE_ONLY = List.of(
            new Order("Kilo",  "APAC", "BOOKS",    20.00),
            new Order("Lima",  "EMEA", "CLOTHING", 75.00),
            new Order("Mike",  "AMER", "BOOKS",    40.00)
    );

    private static void activity4() {
        System.out.println("=== Activity 4: Partitioning + Custom Downstream ===\n");

        System.out.println("-- Part A: partitionBy(amount > 1000) over full dataset --");
        // TODO 4.1 — paste the partitioningBy expression, print with ac3_print(...)
        Map<Boolean, List<Order>> byValue = Order.SAMPLE.stream()
                .collect(partitioningBy(o -> o.amount() > 1000));
        ac3_print("amount > 1000 -> orders", byValue);


        System.out.println("\n-- Part B: the semantic gap (partitioningBy vs groupingBy) --");
        // TODO 4.2 — paste BOTH expressions against LOW_VALUE_ONLY,
        //            print each with ac3_print(...) so keys are visibly different
        Map<Boolean, List<Order>> partitioned = LOW_VALUE_ONLY.stream()
                .collect(partitioningBy(o -> o.amount() > 1000));
        ac3_print("partitioningBy (low-value only)", partitioned);

        Map<Boolean, List<Order>> grouped = LOW_VALUE_ONLY.stream()
                .collect(groupingBy(o -> o.amount() > 1000));
        ac3_print("groupingBy     (low-value only)", grouped);


        System.out.println("\n-- Part C: partition + mapping to customer names --");
        // TODO 4.3 — paste partitioningBy(predicate, mapping(customer, toList())),
        //            print with ac3_print(...)
        Map<Boolean, List<String>> customersByValue = Order.SAMPLE.stream()
                .collect(partitioningBy(
                        o -> o.amount() > 1000,
                        mapping(Order::customer, toList())));
        ac3_print("amount > 1000 -> customer names", customersByValue);

    }

    // ─────────────────────────────────────────────────────────────────────
    //  MENU — main() dispatches to the selected activity
    // ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.println();
                System.out.println("Lab 2.2 — Stream Internals & Collectors");
                System.out.println("  1) Laziness Is Real");
                System.out.println("  2) Short-Circuiting on an Infinite Stream");
                System.out.println("  3) Multi-Level Grouping");
                System.out.println("  4) Partitioning + Custom Downstream");
                System.out.println("  q) quit");
                System.out.print("select> ");

                String choice = in.hasNextLine() ? in.nextLine().trim() : "q";
                switch (choice) {
                    // TODO 1.3 — uncomment the line below AFTER implementing activity1
                    case "1" -> activity1();

                    // TODO 2.3 — uncomment the line below AFTER implementing activity2
                    case "2" -> activity2();

                    // TODO 3.4 — uncomment the line below AFTER implementing activity3
                    case "3" -> activity3();

                    // TODO 4.4 — uncomment the line below AFTER implementing activity4
                    case "4" -> activity4();

                    case "q", "Q", "" -> { return; }
                    default -> System.out.println("unknown option: " + choice);
                }
            }
        }
    }
}

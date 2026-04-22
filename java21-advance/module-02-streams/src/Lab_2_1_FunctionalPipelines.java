import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Lab_2_1_FunctionalPipelines {

    // ─────────────────────────────────────────────
    // Scaffolding — read, don't modify
    // ─────────────────────────────────────────────

    /** Sample strings used across multiple activities. */
    private static final List<String> SAMPLE_INPUTS = List.of(
            "  hello world  ",
            "  functional JAVA  ",
            "  streams ARE powerful  ");

    /**
     * Activity 3 scaffolding — legacy Strategy pattern.
     * Read this code. You will NOT modify it.
     * It exists so you can compare the OO version with
     * the functional replacement you build in Activity 3.
     *
     * --- Legacy Strategy (for comparison only) ---
     *
     * interface TextStrategy {
     * String execute(String input);
     * }
     *
     * class ShoutStrategy implements TextStrategy {
     * public String execute(String input) { return input.toUpperCase(); }
     * }
     *
     * class WhisperStrategy implements TextStrategy {
     * public String execute(String input) { return input.toLowerCase(); }
     * }
     *
     * class RedactStrategy implements TextStrategy {
     * public String execute(String input) {
     * return input.replaceAll("[aeiouAEIOU]", "*");
     * }
     * }
     *
     * // Usage:
     * Map<String, TextStrategy> strategies = new HashMap<>();
     * strategies.put("shout", new ShoutStrategy());
     * strategies.put("whisper", new WhisperStrategy());
     * strategies.put("redact", new RedactStrategy());
     * TextStrategy s = strategies.get("shout");
     * System.out.println(s.execute("Hello"));
     *
     * --- Four files, one interface, three classes. ---
     */

    /**
     * Activity 5 scaffolding — generates a large dataset and checks
     * result integrity after a stream operation.
     */
    private static List<String> generateLargeDataset(int size) {
        List<String> data = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            data.add("item-" + i);
        }
        return data;
    }

    private static void checkIntegrity(String label, List<String> source, List<String> result) {
        int expected = source.size();
        int actual = result.size();
        long distinctCount = result.stream().distinct().count();

        System.out.println("\n  [Integrity Check — " + label + "]");
        System.out.println("  Expected size : " + expected);
        System.out.println("  Actual size   : " + actual);
        System.out.println("  Distinct items: " + distinctCount);

        if (actual != expected) {
            System.out.println("  ⚠ SIZE MISMATCH — items were lost or duplicated!");
        }
        if (distinctCount != actual) {
            System.out.println("  ⚠ DUPLICATES DETECTED — " + (actual - distinctCount) + " duplicate(s)!");
        }
        if (actual == expected && distinctCount == actual) {
            System.out.println("  ✓ Clean — all items accounted for.");
        }
    }

    // ═════════════════════════════════════════════
    // Activity 1 — Function as First-Class Data
    // ═════════════════════════════════════════════

    // TODO 1.1 — Create a method called ac1_applyAndPrint

    private static void ac1_applyAndPrint(Function<String, String> fn, String input, String label) {
        String result = fn.apply(input);
        System.out.println("  [" + label + "] -> \"" + result + "\"");
    }

    // TODO 1.2 - create two Function<String, String> fields
    private static final Function<String, String> ac1_trim = String::trim;
    private static final Function<String, String> ac1_upper = String::toUpperCase;

    // TODO 1.3 — uncomment the activity1() method below
    private static void activity1() {
        System.out.println("Activity 1 — Function as First-Class Data\n");
        System.out.println("Passing Function objects to a method, just like any other data:\n");
        for (String s : SAMPLE_INPUTS) {
            System.out.println("  Original:    \"" + s + "\"");
            ac1_applyAndPrint(ac1_trim, s, "Trimmed");
            ac1_applyAndPrint(ac1_upper, s, "Uppercased");
            System.out.println();
        }
    }

    // ═════════════════════════════════════════════
    // Activity 2 — Composition: andThen vs compose
    // ═════════════════════════════════════════════

    /** Base functions — provided so you can focus on composition. */
    private static final Function<String, String> trim = String::trim;
    private static final Function<String, String> toUpper = String::toUpperCase;
    private static final Function<String, String> addBrackets = s -> "[" + s + "]";

    // TODO 2.1 — Create two composed Function<String, String> fields
    private static final Function<String, String> ac2_trimThenUpper = trim.andThen(toUpper);

    private static final Function<String, String> ac2_upperComposeTrim = toUpper.compose(trim);

    // TODO 2.2 — This chains three functions to show longer pipelines.

    private static final Function<String, String> ac2_threeStage = trim.andThen(toUpper).andThen(addBrackets);

    // TODO 2.3 — uncomment the activity2() method below
    private static void activity2() {
        System.out.println("Activity 2 — Composition: andThen vs compose\n");
        for (String s : SAMPLE_INPUTS) {
            System.out.println("  Original            : \"" + s + "\"");
            System.out.println("  trim.andThen(upper) : \"" + ac2_trimThenUpper.apply(s) + "\"");
            System.out.println("  upper.compose(trim) : \"" + ac2_upperComposeTrim.apply(s) + "\"");
            System.out.println("  Three-stage pipeline: \"" + ac2_threeStage.apply(s) + "\"");
            System.out.println();
        }
        System.out.println("  Notice: andThen reads left-to-right, compose reads right-to-left.");
        System.out.println("  trim.andThen(upper) and upper.compose(trim) produce the SAME result.");
        System.out.println("  Reverse either one and the output changes.\n");
    }

    // ═════════════════════════════════════════════
    // Activity 3 — Dynamic Strategy with Function Maps
    // ═════════════════════════════════════════════

    // TODO 3.1 — Create a method called ac3_buildStrategyMap
    private static Map<String, Function<String, String>> ac3_buildStrategyMap() {
        Map<String, Function<String, String>> map = new HashMap<>();
        map.put("shout", String::toUpperCase);
        map.put("whisper", String::toLowerCase);
        map.put("redact", s -> s.replaceAll("[aeiouAEIOU]", "*"));
        return map;
    }

    // TODO 3.2 — create a method called ac3_applyStrategy
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

    // TODO 3.3 — uncomment the activity3() method below
    private static void activity3() {
        System.out.println("Activity 3 — Dynamic Strategy with Function Maps\n");

        Map<String, Function<String, String>> strategies = ac3_buildStrategyMap();

        System.out.println("  Available strategies: " + strategies.keySet());
        System.out.println();

        String testInput = "Functional Java";
        System.out.println("  Input: \"" + testInput + "\"\n");

        ac3_applyStrategy(strategies, "shout", testInput);
        ac3_applyStrategy(strategies, "whisper", testInput);
        ac3_applyStrategy(strategies, "redact", testInput);
        ac3_applyStrategy(strategies, "unknown", testInput);

        System.out.println("\n  Compare this with the legacy Strategy pattern in the");
        System.out.println("  scaffolding comment above — same behaviour, zero class hierarchy.");
    }

    // ═════════════════════════════════════════════
    // Activity 4 — Effectively Final: The Boundary
    // ═════════════════════════════════════════════

    private static void activity4() {
        System.out.println("Activity 4 — Effectively Final: The Boundary\n");

        // --- Part A: Reassignment (compiler will reject this) ---

        System.out.println("  Part A: Attempting to reassign a captured variable...");
        System.out.println("  (See commented-out code — compiler rejected it.)\n");

        // TODO 4.1 — write and then comment out the reassignment attempt here

        // Compiler error: local variable 'message' is not effectively final
        // String message = "hello";
        // SAMPLE_INPUTS.forEach(s -> {
        //     message = s; // ← reassignment — compiler rejects this
        // });
        // System.out.println(message);
        // --- Part B: Mutation through a captured reference ---

        System.out.println("  Part B: Mutating a captured List reference...\n");

        List<String> collected = new ArrayList<>();

        // TODO 4.2 — uncomment the forEach below that mutates 'collected'
        SAMPLE_INPUTS.forEach(s -> collected.add(s.trim().toUpperCase()));

        // TODO 4.3 — uncomment the print below to reveal the mutated list
        System.out.println(" Collected via mutation: " + collected);
        System.out.println();
        System.out.println(" The reference 'collected' was never reassigned — it's effectively final.");
        System.out.println(" But the List CONTENTS changed. The compiler can't protect you here.");
        System.out.println(" 'Effectively final' guards the REFERENCE, not the STATE.");
    }

    // ═════════════════════════════════════════════
    // Activity 5 — Illegal State Mutation in Closures
    // ═════════════════════════════════════════════

    private static void activity5() {
        System.out.println("Activity 5 — Illegal State Mutation in Closures\n");

        List<String> sourceData = generateLargeDataset(10_000);

        // --- Part A: Sequential mutation (appears to work) ---

        // TODO 5.1 — Create an ArrayList<String> called 'resultA'.
        List<String> resultA = new ArrayList<>();
        sourceData.stream().forEach(s -> resultA.add(s.toUpperCase()));
        checkIntegrity("Sequential forEach", sourceData, resultA);

        // --- Part B: Parallel mutation (corruption) ---

        System.out.println("\n  Now switching to parallelStream...\n");

        // TODO 5.2 — uncomment the parallel mutation block below
        List<String> resultB = new ArrayList<>();
        sourceData.parallelStream().forEach(s -> resultB.add(s.toUpperCase()));
        checkIntegrity("Parallel forEach", sourceData, resultB);

        // --- Part C: Safe collection (the fix) ---

        System.out.println("\n  Fixing with Collectors.toList()...\n");

        // TODO 5.3 — uncomment the safe collect block below
        List<String> resultC = sourceData.parallelStream()
        .map(String::toUpperCase)
        .collect(Collectors.toList());
        checkIntegrity("Parallel collect", sourceData, resultC);
        
        System.out.println("\n The rule: NEVER mutate external state inside a stream.");
        System.out.println(" Use .collect() — it's designed for safe, parallel accumulation.");
    }

    // ═════════════════════════════════════════════
    // Menu Runner
    // ═════════════════════════════════════════════

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║  Lab 2.1 — Higher-Order Functions &         ║");
            System.out.println("║            Closure Discipline               ║");
            System.out.println("╠══════════════════════════════════════════════╣");
            System.out.println("║  1. Function as First-Class Data            ║");
            System.out.println("║  2. Composition: andThen vs compose         ║");
            System.out.println("║  3. Dynamic Strategy with Function Maps     ║");
            System.out.println("║  4. Effectively Final: The Boundary         ║");
            System.out.println("║  5. Illegal State Mutation in Closures      ║");
            System.out.println("║  0. Exit                                    ║");
            System.out.println("╚══════════════════════════════════════════════╝");
            System.out.print("Select activity: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                // TODO 1.3 — after uncommenting activity1(), uncomment the case below
                case "1" -> activity1();

                // TODO 2.3 — after uncommenting activity2(), uncomment the case below
                case "2" -> activity2();

                // TODO 3.2 — after uncommenting activity3(), uncomment the case below
                case "3" -> activity3();

                case "4" -> activity4();
                case "5" -> activity5();
                case "0" -> {
                    System.out.println("Done.");
                    scanner.close();
                    return;
                }
                default -> System.out.println("Invalid choice. Try again.");
            }
        }
    }
}

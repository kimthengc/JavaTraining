import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.stream.IntStream;

public class Lab_2_3_PipelineEngineering {

    // ─────────────────────────────────────────────────────────────────────
    // Shared scaffolding
    // ─────────────────────────────────────────────────────────────────────

    /** Dataset size for Activity 1. Large enough that boxing costs show up. */
    private static final int N = 10_000_000;

    /** Sample strings used by Activity 2. Mixed case, some whitespace. */
    private static final List<String> SAMPLE_INPUTS = List.of(
            "  hello  ",
            "World",
            "  functional  ",
            "Java");

    /**
     * Input IDs used by Activity 3. All succeed — no "BAD" in this list,
     * so the pipelines complete cleanly. Add "BAD" to see the wrapped
     * RuntimeException propagate if you want to experiment.
     */
    private static final List<String> INPUTS = List.of("A1", "A2", "A3", "A4");

    /**
     * Dataset for Activity 4. 32 elements × 50ms per element = enough work
     * to make pool contention visible at any parallelism level ≥ 2.
     */
    private static final List<String> DATA = IntStream.range(0, 32)
            .mapToObj(i -> "item-" + i)
            .collect(toList());

    // ─────────────────────────────────────────────────────────────────────
    // Activity 1 — The Boxing Tax
    // ─────────────────────────────────────────────────────────────────────

    // TODO 1.1 — create ac1_boxedSumOfSquares
    private static long ac1_boxedSumOfSquares() {
        return IntStream.rangeClosed(1, N).boxed()
                .map(i -> (long) i * i)
                .reduce(0L, Long::sum);
    }

    // TODO 1.2 — create ac1_primitiveSumOfSquares
    private static long ac1_primitiveSumOfSquares() {
        return IntStream.rangeClosed(1, N)
                .mapToLong(i -> (long) i * i)
                .sum();
    }

    // TODO 1.3 — uncomment activity1() below after 1.1 and 1.2 are pasted

    private static void activity1() {
        System.out.println("Activity 1 — The Boxing Tax");
        System.out.println("  N = " + N);

        // Warm-up run — let the JIT compile hot paths before we time anything.
        // Without this, the first pipeline to run always looks slower,
        // regardless of which one it is.
        ac1_boxedSumOfSquares();
        ac1_primitiveSumOfSquares();

        long startBoxed = System.nanoTime();
        long boxedResult = ac1_boxedSumOfSquares();
        long elapsedBoxed = (System.nanoTime() - startBoxed) / 1_000_000;

        long startPrimitive = System.nanoTime();
        long primitiveResult = ac1_primitiveSumOfSquares();
        long elapsedPrimitive = (System.nanoTime() - startPrimitive) / 1_000_000;

        System.out.println("  Stream<Long>     result: " + boxedResult
                + "   elapsed: " + elapsedBoxed + " ms");
        System.out.println("  IntStream        result: " + primitiveResult
                + "   elapsed: " + elapsedPrimitive + " ms");
        System.out.println("  ratio (boxed / primitive): "
                + String.format("%.2fx", (double) elapsedBoxed / elapsedPrimitive));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Activity 2 — Method References
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Static helper for the static-method-reference demonstration in Activity 2.
     */
    private static String ac2_shout(String s) {
        return s.toUpperCase();
    }

    private static void activity2() {
        System.out.println("Activity 2 — Method References");

        // TODO 2.1 — three equivalent pipelines: lambda, instance::ref, static::ref
        System.out.println("  --- Part A: three equivalent pipelines ---");

        List<String> viaLambda = SAMPLE_INPUTS.stream()
                .map(s -> s.toUpperCase())
                .collect(toList());
        System.out.println("  lambda                 -> " + viaLambda);

        List<String> viaInstanceRef = SAMPLE_INPUTS.stream()
                .map(String::toUpperCase)
                .collect(toList());
        System.out.println("  String::toUpperCase    -> " + viaInstanceRef);

        List<String> viaStaticRef = SAMPLE_INPUTS.stream()
                .map(Lab_2_3_PipelineEngineering::ac2_shout)
                .collect(toList());
        System.out.println("  Lab::ac2_shout         -> " + viaStaticRef);

        // TODO 2.2 — compile-error demonstration.
        //
        // A method reference names exactly ONE existing method. The lambda
        // s -> s.toUpperCase().trim() does two things — it has no single
        // JDK method it can point at. There is no String::toUpperAndTrim.

        // Works — lambda with two method calls in the body:
        List<String> viaLambda2 = SAMPLE_INPUTS.stream()
                .map(s -> s.toUpperCase().trim())
                .collect(toList());

        // Fails — no such method on String, so no method reference exists:
        // Function<String, String> upperThenTrim = String::toUpperAndTrim;

    }

    // TODO 2.3 — uncomment `case "2" -> activity2();` in main()

    // ─────────────────────────────────────────────────────────────────────
    // Activity 3 — Checked Exceptions in Pipelines
    // ─────────────────────────────────────────────────────────────────────

    // TODO 3.4 — create the unchecked() helper

    private static void activity3() {
        System.out.println("Activity 3 — Checked Exceptions in Pipelines");

        // TODO 3.1 — uncomment the line below to bring CheckedService into scope.
        CheckedService service = new CheckedService();

        // TODO 3.2 — broken direct call.

        // List<String> direct = INPUTS.stream()
        // .map(service::lookup) // ← IOException has nowhere to go
        // .collect(toList());
        // System.out.println(" direct: " + direct);

        // TODO 3.3 — inline try/catch wrapper (Part B)
        System.out.println("  --- Part B: inline try/catch wrapper ---");

        List<String> inlineResults = INPUTS.stream()
                .map(id -> {
                    try {
                        return service.lookup(id);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(toList());

        System.out.println("  results: " + inlineResults);

        // TODO 3.5 — unchecked() helper (Part C)
        System.out.println("  --- Part C: unchecked() helper ---");

        List<String> helperResults = INPUTS.stream()
                .map(unchecked(service::lookup))
                .collect(toList());

        System.out.println("  results: " + helperResults);

    }

    // TODO 3.4 — create the unchecked() helper (Part C)
    private static <T, R> Function<T, R> unchecked(CheckedFunction<T, R> fn) {
        return t -> {
            try {
                return fn.apply(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    // TODO 3.6 — uncomment `case "3" -> activity3();` in main()

    // ─────────────────────────────────────────────────────────────────────
    // Activity 4 — Parallel Stream Contention
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Artificial slow work. Sleeps 50ms per element so wall-time differences
     * between pipelines are visible by eye, not just via nanoTime.
     */
    private static String ac4_slowUpper(String s) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return s.toUpperCase();
    }

    private static void activity4() throws Exception {
        System.out.println("Activity 4 — Parallel Stream Contention");
        System.out.println("  commonPool parallelism: "
                + ForkJoinPool.commonPool().getParallelism());
        System.out.println("  dataset size: " + DATA.size()
                + "   per-element cost: 50 ms");

        // TODO 4.1 — Part A: one parallel pipeline alone
        System.out.println("  --- Part A: one parallel pipeline alone ---");

        long startA = System.nanoTime();
        List<String> resultA = DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList());
        long elapsedA = (System.nanoTime() - startA) / 1_000_000;

        System.out.println("  items processed: " + resultA.size());
        System.out.println("  elapsed:         " + elapsedA + " ms");

        // TODO 4.2 — Part B: two parallel pipelines, shared common pool
        System.out.println("Starving Common Pool ...");
        int parallelism = ForkJoinPool.getCommonPoolParallelism();
        for (int i = 0; i < parallelism; i++) {
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    Thread.sleep(5_000);
                } catch (Exception e) {
                }
            });
        }
        System.out.println("Common Pool is now full and 'starved'...");

        System.out.println("  --- Part B: two parallel pipelines, shared common pool ---");

        long startB = System.nanoTime();
        Thread t1 = Thread.ofPlatform().start(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        Thread t2 = Thread.ofPlatform().start(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        t1.join();
        t2.join();
        long elapsedB = (System.nanoTime() - startB) / 1_000_000;

        System.out.println("  elapsed:         " + elapsedB + " ms");
        System.out.println("  ratio vs Part A: " + String.format("%.2fx", (double) elapsedB / elapsedA));

        // TODO 4.3 — Part C: two parallel pipelines, dedicated pools
        Thread.sleep(6_000);
        System.out.println("  --- Part C: two parallel pipelines, dedicated pools ---");

        ForkJoinPool pool1 = new ForkJoinPool(4);
        ForkJoinPool pool2 = new ForkJoinPool(4);

        long startC = System.nanoTime();
        ForkJoinTask<List<String>> task1 = pool1.submit(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        ForkJoinTask<List<String>> task2 = pool2.submit(() -> DATA.stream()
                .parallel()
                .map(Lab_2_3_PipelineEngineering::ac4_slowUpper)
                .collect(toList()));
        task1.get();
        task2.get();
        long elapsedC = (System.nanoTime() - startC) / 1_000_000;

        pool1.shutdown();
        pool2.shutdown();

        System.out.println("  elapsed:         " + elapsedC + " ms");
        System.out.println("  ratio vs Part A: " + String.format("%.2fx", (double) elapsedC / elapsedA));

    }

    // TODO 4.4 — uncomment `case "4" -> activity4();` in main()

    // ─────────────────────────────────────────────────────────────────────
    // Menu-driven runner
    // ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.println("Lab 2.3 — Pipeline Engineering");
            System.out.println("  1) The Boxing Tax");
            System.out.println("  2) Method References");
            System.out.println("  3) Checked Exceptions in Pipelines");
            System.out.println("  4) Parallel Stream Contention");
            System.out.println("  q) quit");
            System.out.print("Select: ");

            String choice = scanner.nextLine().trim();
            System.out.println();

            switch (choice) {
                case "1" -> activity1();
                case "2" -> activity2();
                case "3" -> activity3();
                case "4" -> activity4();
                case "q", "Q" -> {
                    return;
                }
                default -> System.out.println("Unknown selection: " + choice);
            }
        }
    }
}

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Lab_4_3_CompletableFuture {

    // ─────────────────────────────────────────────────────────────────────
    // Activity 1 scaffolding -- User/Profile records + async/blocking helpers
    // ─────────────────────────────────────────────────────────────────────

    private static final int STAGE_DURATION_MS = 300;

    record User(String username, Long id) {
    }

    record Profile(Long userId, String displayName, String email) {
    }

    /**
     * Async lookup -- returns a CompletableFuture that will complete after
     * STAGE_DURATION_MS.
     */
    private static CompletableFuture<Long> lookupUserIdAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(STAGE_DURATION_MS);
            return (long) username.hashCode();
        });
    }

    /**
     * Async fetch -- returns a CompletableFuture that will complete after
     * STAGE_DURATION_MS.
     */
    private static CompletableFuture<Profile> fetchProfileAsync(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(STAGE_DURATION_MS);
            return new Profile(userId, "user-" + userId, "user-" + userId + "@example.com");
        });
    }

    /** Blocking equivalent -- runs on the calling thread, returns directly. */
    private static Long lookupUserIdBlocking(String username) {
        sleep(STAGE_DURATION_MS);
        return (long) username.hashCode();
    }

    /** Blocking equivalent -- runs on the calling thread, returns directly. */
    private static Profile fetchProfileBlocking(Long userId) {
        sleep(STAGE_DURATION_MS);
        return new Profile(userId, "user-" + userId, "user-" + userId + "@example.com");
    }

    // TODO 1.1 -- Create method ac1_runFuturePipeline
    private static Profile ac1_runFuturePipeline(String username) {
        long start = System.nanoTime();
        System.out.println("  [Future] main thread: " + Thread.currentThread().getName());

        Long userId = lookupUserIdBlocking(username);
        System.out.println("  [Future] stage 1 done at t=" + ms(start) + "ms (main BLOCKED)");

        Profile profile = fetchProfileBlocking(userId);
        System.out.println("  [Future] stage 2 done at t=" + ms(start) + "ms (main BLOCKED)");

        return profile;
    }

    // TODO 1.2 -- Create method ac1_runBrokenCfPipeline (deliberately uses
    // thenApply)

    private static CompletableFuture<CompletableFuture<Profile>> ac1_runBrokenCfPipeline(String username) {
        return lookupUserIdAsync(username)
                .thenApply(userId -> fetchProfileAsync(userId));
    }

    // TODO 1.3 -- Create method ac1_runFixedCfPipeline (uses thenCompose)
    private static CompletableFuture<Profile> ac1_runFixedCfPipeline(String username) {
        return lookupUserIdAsync(username)
                .thenCompose(userId -> fetchProfileAsync(userId));
    }

    // TODO 1.4 -- Uncomment activity1() below

    private static void activity1() {
        System.out.println();
        System.out.println("=== Future pipeline (blocking) ===");
        long start = System.nanoTime();
        Profile p1 = ac1_runFuturePipeline("alice");
        System.out.println("  [Future] result: " + p1);
        System.out.println("  [Future] total elapsed: " + ms(start) + "ms");

        System.out.println();
        System.out.println("=== Broken CF pipeline (thenApply on async stage) ===");
        start = System.nanoTime();
        CompletableFuture<CompletableFuture<Profile>> nested = ac1_runBrokenCfPipeline("alice");
        System.out.println("  [CF-broken] main free, doing other work...");
        for (int i = 0; i < 3; i++) {
            sleep(100);
            System.out.println("  [CF-broken] tick " + (i + 1));
        }
        Object result = nested.join();
        System.out.println("  [CF-broken] join() returned: " + result);
        System.out.println("  [CF-broken] type is: " + result.getClass().getSimpleName() + " -- NOT a Profile!");
        System.out.println("  [CF-broken] total elapsed: " + ms(start) + "ms");

        System.out.println();
        System.out.println("=== Fixed CF pipeline (thenCompose) ===");
        start = System.nanoTime();
        CompletableFuture<Profile> flat = ac1_runFixedCfPipeline("alice");
        System.out.println("  [CF-fixed] main free, doing other work...");
        for (int i = 0; i < 3; i++) {
            sleep(100);
            System.out.println("  [CF-fixed] tick " + (i + 1));
        }
        Profile p2 = flat.join();
        System.out.println("  [CF-fixed] join() returned: " + p2);
        System.out.println("  [CF-fixed] total elapsed: " + ms(start) + "ms");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Activity 2 scaffolding -- three tasks of different durations
    // ─────────────────────────────────────────────────────────────────────

    private static final int AC2_FAST_MS = 200;
    private static final int AC2_MEDIUM_MS = 400;
    private static final int AC2_SLOW_MS = 300;

    /** Returns a Supplier that sleeps for durationMs and then returns the label. */
    private static Supplier<String> slowSupplier(String label, int durationMs) {
        return () -> {
            sleep(durationMs);
            return label;
        };
    }

    // TODO 2.1 -- Create method ac2_runSequential
    private static long ac2_runSequential() throws Exception {
        long start = System.nanoTime();
        String r1 = CompletableFuture.supplyAsync(slowSupplier("fast", AC2_FAST_MS)).get();
        String r2 = CompletableFuture.supplyAsync(slowSupplier("medium", AC2_MEDIUM_MS)).get();
        String r3 = CompletableFuture.supplyAsync(slowSupplier("slow", AC2_SLOW_MS)).get();
        System.out.println("  [seq] results: " + r1 + ", " + r2 + ", " + r3);
        return ms(start);
    }

    // TODO 2.2 -- Create method ac2_runParallelBroken
    private static long ac2_runParallelBroken() throws Exception {
        long start = System.nanoTime();
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(slowSupplier("fast", AC2_FAST_MS));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(slowSupplier("medium", AC2_MEDIUM_MS));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(slowSupplier("slow", AC2_SLOW_MS));

        CompletableFuture<Void> all = CompletableFuture.allOf(cf1, cf2, cf3);
        Void nothing = all.get();
        System.out.println("  [parallel-broken] allOf returned: " + nothing + " (literally null, no results inside)");
        return ms(start);
    }

    // TODO 2.3 -- Create method ac2_runParallelCorrect
    private static long ac2_runParallelCorrect() throws Exception {
        long start = System.nanoTime();
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(slowSupplier("fast", AC2_FAST_MS));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(slowSupplier("medium", AC2_MEDIUM_MS));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(slowSupplier("slow", AC2_SLOW_MS));

        CompletableFuture.allOf(cf1, cf2, cf3).join();
        String r1 = cf1.join();
        String r2 = cf2.join();
        String r3 = cf3.join();
        System.out.println("  [parallel] results: " + r1 + ", " + r2 + ", " + r3);
        return ms(start);
    }

    // TODO 2.4 -- Uncomment activity2() below

    private static void activity2() throws Exception {
        System.out.println();
        System.out.println("=== Sequential (.get() between submissions) ===");
        long seq = ac2_runSequential();
        System.out.println(" [seq] elapsed: " + seq + "ms");

        System.out.println();
        System.out.println("=== Parallel -- broken (allOf returns Void) ===");
        long brk = ac2_runParallelBroken();
        System.out.println(" [parallel-broken] elapsed: " + brk + "ms");

        System.out.println();
        System.out.println("=== Parallel -- correct (.join() each original CF) ===");
        long par = ac2_runParallelCorrect();
        System.out.println(" [parallel] elapsed: " + par + "ms");

        System.out.println();
        System.out.println("=== Summary ===");
        System.out.println(" sequential: " + seq + "ms (sum of all three task durations)");
        System.out.println(" parallel-broken: " + brk + "ms (parallel worked, results lost)");
        System.out.println(" parallel-correct: " + par + "ms (parallel + harvested)");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Activity 3 scaffolding -- SLA constants + service helper
    // ─────────────────────────────────────────────────────────────────────

    private static final int AC3_TIMEOUT_MS = 200;
    private static final int AC3_FAST_TASK_MS = 50;
    private static final int AC3_SLOW_TASK_MS = 1000;

    /** Async "service call" -- sleeps for durationMs, then returns result. */
    private static CompletableFuture<String> callServiceAsync(int durationMs, String result) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(durationMs);
            return result;
        });
    }

    // TODO 3.1 -- Create method ac3_callWithSla
    private static String ac3_callWithSla(int durationMs) {
        return callServiceAsync(durationMs, "real-result")
                .orTimeout(AC3_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> "<fallback: " + ex.getClass().getSimpleName() + ">")
                .join();
    }

    // TODO 3.2 -- Uncomment activity3() below

    private static void activity3() {
        System.out.println();
        System.out.println("=== Happy path: task " + AC3_FAST_TASK_MS + "ms vs SLA "
                + AC3_TIMEOUT_MS + "ms ===");
        long start = System.nanoTime();
        String fast = ac3_callWithSla(AC3_FAST_TASK_MS);
        System.out.println(" [happy] result: " + fast);
        System.out.println(" [happy] elapsed: " + ms(start) + "ms");

        System.out.println();
        System.out.println("=== Timeout path: task " + AC3_SLOW_TASK_MS + "ms vs SLA " + AC3_TIMEOUT_MS + "ms ===");
        start = System.nanoTime();
        String slow = ac3_callWithSla(AC3_SLOW_TASK_MS);
        System.out.println(" [timeout] result: " + slow);
        System.out.println(" [timeout] elapsed: " + ms(start) + "ms (SLA fired, slow task abandoned)");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Menu-driven runner
    // ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println();
                System.out.println("Lab 4.3 -- CompletableFuture & Async Composition");
                System.out.println("  1) Future is a wall, CompletableFuture is a pipe");
                System.out.println("  2) allOf returns Void");
                System.out.println("  3) SLA enforcement: exceptionally + orTimeout");
                System.out.println("  q) quit");
                System.out.print("> ");

                String choice = sc.hasNextLine() ? sc.nextLine().trim() : "q";
                switch (choice) {
                    case "1" -> activity1();
                    case "2" -> activity2();
                    case "3" -> activity3();
                    case "q", "Q" -> {
                        return;
                    }
                    default -> System.out.println("Unknown choice: " + choice);
                }
            }
        }
    }
}

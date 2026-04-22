import java.lang.ScopedValue;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

//import java.util.NoSuchElementException; 

/**
 * Preview-flag callout
 * --------------------
 * Both ScopedValue (JEP 446) and StructuredTaskScope (JEP 453) are PREVIEW APIs
 * in Java 21. Compile and run with the --enable-preview flag:
 *
 * javac --enable-preview --release 21 Lab_5_2_StructuredConcurrency.java
 * java --enable-preview Lab_5_2_StructuredConcurrency
 *
 * Without these flags, the file will not compile (javac) or will refuse to load
 * (java). The compiler's note "uses preview features of Java SE 21" is harmless
 * and confirms preview mode is active.
 */
public class Lab_5_2_StructuredConcurrency {

    // ========================================================================
    // Activity 1 scaffolding -- ScopedValue replacing ThreadLocal
    // ========================================================================
    //
    // ThreadLocal vs ScopedValue API surface, side by side:
    //
    // ThreadLocal<String> USER = new ThreadLocal<>();
    // USER.set("alice"); // mutable, settable anywhere
    // String u = USER.get(); // value persists for life of thread
    // USER.remove(); // must be called manually to avoid leaks
    //
    // ScopedValue<String> USER = ScopedValue.newInstance();
    // ScopedValue.where(USER, "alice").run(() -> {
    // String u = USER.get(); // value visible only inside this block
    // }); // value evaporates when block returns
    // // USER.get() out here -> NoSuchElementException
    //
    // The ScopedValue type has no setter. The lifetime is bounded by the
    // run(...) call. Both properties are enforced -- one by the compiler,
    // one by the runtime.

    private static final ScopedValue<String> AC1_USER = ScopedValue.newInstance();

    /** Helper that reads AC1_USER deep in a call stack -- no parameter passed. */
    private static void ac1_readUserDeepInCallStack() {
        System.out.println("    [deep call] current user: " + AC1_USER.get());
    }

    // TODO 1.1 -- paste the ThreadLocal "before" demo block
    private static final ThreadLocal<String> AC1_USER_TL = new ThreadLocal<>();

    private static void ac1_threadLocalDemo() {
        AC1_USER_TL.set("alice");
        System.out.println("    [outer]     current user: " + AC1_USER_TL.get());
        ac1_readUserDeepInCallStackTL();
        AC1_USER_TL.set("bob"); // any code can mutate it, anywhere
        System.out.println("    [outer]     current user: " + AC1_USER_TL.get() + "  <-- mutated mid-flight");
        AC1_USER_TL.remove(); // manual cleanup required to avoid leaks
    }

    private static void ac1_readUserDeepInCallStackTL() {
        System.out.println("    [deep call] current user: " + AC1_USER_TL.get());
    }

    // TODO 1.2 -- paste the broken ScopedValue.set(...) misuse
    // private static void ac1_scopedValueBroken() {
    // AC1_USER.set("alice"); // <-- compiler rejects: no method set(String)
    // System.out.println(" current user: " + AC1_USER.get());
    // }

    // TODO 1.3 -- paste the corrected ScopedValue.where(...).run(...) form
    // plus the out-of-scope read demonstration
    private static void ac1_scopedValueDemo() {
        ScopedValue.where(AC1_USER, "alice").run(() -> {
            System.out.println("    [outer]     current user: " + AC1_USER.get());
            ac1_readUserDeepInCallStack();
        });
    }

    private static void ac1_outsideScopeDemo() {
        System.out.println("    AC1_USER.isBound() outside any scope: " + AC1_USER.isBound());
        try {
            String leaked = AC1_USER.get();
            System.out.println("    leaked value: " + leaked); // unreachable
        } catch (NoSuchElementException e) {
            System.out.println("    AC1_USER.get() outside scope threw: " + e.getClass().getSimpleName());
        }
    }

    // TODO 1.4 -- uncomment activity1() once the three pastes above are in place

    private static void activity1() {
        System.out.println("=== Activity 1: ScopedValue replacing ThreadLocal ===");

        System.out.println();
        System.out.println(" -- Part A: ThreadLocal (the old way) --");
        ac1_threadLocalDemo();

        System.out.println();
        System.out.println(" -- Part B: ScopedValue (the new way) --");
        ac1_scopedValueDemo();

        System.out.println();
        System.out.println(" -- Part C: lifetime boundary --");
        ac1_outsideScopeDemo();
    }

    // ========================================================================
    // Activity 2 scaffolding -- StructuredTaskScope.ShutdownOnFailure
    // ========================================================================
    //
    // Three "remote calls" -- sleep stand-ins for HTTP fetches against
    // independent backend services. Different durations make sibling
    // cancellation visible in the timing.
    //
    // The failing variant of fetchOrders throws after 200ms. When wired into
    // a ShutdownOnFailure scope, the throw triggers cancellation of the other
    // two siblings -- profile (300ms) and recommendations (900ms) -- before
    // they complete. Wall-clock elapsed should be near 200ms, not near 900ms.

    private static String ac2_fetchProfile() throws InterruptedException {
        Thread.sleep(300);
        return "Profile{name=Alice,id=42}";
    }

    private static String ac2_fetchOrders() throws InterruptedException {
        Thread.sleep(600);
        return "Orders[12 items]";
    }

    private static String ac2_fetchRecommendations() throws InterruptedException {
        Thread.sleep(900);
        return "Recs[8 items]";
    }

    /** Failing variant of fetchOrders -- simulates a downstream service failure. */
    private static String ac2_fetchOrdersFailing() throws InterruptedException {
        Thread.sleep(200);
        throw new RuntimeException("orders service unavailable");
    }

    // TODO 2.1 -- paste the happy-path ShutdownOnFailure block (ac2_runHappyPath)
    private static void ac2_runHappyPath() throws Exception {
        long start = System.nanoTime();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<String> profile = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchProfile);
            Subtask<String> orders = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchOrders);
            Subtask<String> recs = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchRecommendations);

            scope.join();
            scope.throwIfFailed();

            System.out.println("    profile: " + profile.get());
            System.out.println("    orders : " + orders.get());
            System.out.println("    recs   : " + recs.get());
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("    elapsed: " + elapsed + " ms");
    }

    // TODO 2.2 -- paste the failure-path block (ac2_runFailurePath)
    private static void ac2_runFailurePath() throws Exception {
        long start = System.nanoTime();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<String> profile = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchProfile);
            Subtask<String> orders = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchOrdersFailing);
            Subtask<String> recs = scope.fork(Lab_5_2_StructuredConcurrency::ac2_fetchRecommendations);

            scope.join(); // waits for all to finish OR for first failure to trigger shutdown

            System.out.println("    profile state: " + profile.state());
            System.out.println("    orders  state: " + orders.state());
            System.out.println("    recs    state: " + recs.state());

            try {
                scope.throwIfFailed();
            } catch (Exception e) {
                System.out.println("    scope reported failure: "
                        + e.getClass().getSimpleName()
                        + " caused by "
                        + e.getCause().getClass().getSimpleName()
                        + ": " + e.getCause().getMessage());
            }
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("    elapsed: " + elapsed + " ms");
    }

    // TODO 2.3 -- uncomment activity2() once both helpers are pasted

    private static void activity2() throws Exception {
    System.out.println("=== Activity 2: StructuredTaskScope -- treating N tasks as one ===");
    System.out.println(" Three concurrent 'remote calls' inside one structured scope.");
    System.out.println(" Profile=300ms, Orders=600ms, Recommendations=900ms.");
    System.out.println();
    
    System.out.println(" [happy path] all three succeed -- elapsed should be ~longest task (~900ms)");
    ac2_runHappyPath();
    System.out.println();
    
    System.out.println(" [failure path] orders throws at 200ms -- siblings should be cancelled");
    ac2_runFailurePath();
    }

    // ========================================================================
    // Menu-driven runner
    // ========================================================================

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println();
                System.out.println("=== Lab 5.2 -- Structured Concurrency with Loom ===");
                System.out.println("  1) ScopedValue replacing ThreadLocal");
                System.out.println("  2) StructuredTaskScope -- treating N tasks as one");
                System.out.println("  q) quit");
                System.out.print("Select: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> activity1();
                    case "2" -> activity2();
                    case "q", "Q" -> {
                        return;
                    }
                    default -> System.out.println("Unknown choice: " + choice);
                }
            }
        }
    }
}

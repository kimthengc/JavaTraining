import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;


public class Lab_3_3_WatchService {

    /* ---------- Shared scaffolding ---------- */

    // Root working directory for all activities. Each activity creates its
    // own subdirectory underneath this to isolate its events.
    private static final Path WORK_DIR = Path.of(
            System.getProperty("java.io.tmpdir"), "lab_3_3_watch");

    // Shared shutdown flag for helper threads. Set to false at the end of
    // each activity so any still-running helper exits promptly.
    private static final AtomicBoolean running = new AtomicBoolean(true);

    /** Uninterruptible sleep helper used by the helper threads. */
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    /** Creates (or clears) a per-activity subdirectory under WORK_DIR. */
    private static Path freshActivityDir(String name) throws IOException {
        Path dir = WORK_DIR.resolve(name);
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Helper thread body for Activity 1. Creates, modifies, and deletes a
     * single file in the watched directory, with short pauses between each
     * action so the events arrive one at a time rather than coalescing.
     */
    private static void ac1_helperThread(Path watched) {
        try {
            Path target = watched.resolve("target.txt");
            sleep(200);
            Files.writeString(target, "first content");       // ENTRY_CREATE
            sleep(300);
            Files.writeString(target, "second content");      // ENTRY_MODIFY
            sleep(300);
            Files.deleteIfExists(target);                     // ENTRY_DELETE
        } catch (IOException ignored) {}
    }


    /* ========================================================================
     *  Activity 1: Register a Watch, See Events
     *  -------------------------------------------------------------------
     *  Register a WatchService on a temp directory, let a helper thread
     *  create/modify/delete a file, and print each event as it arrives.
     *  First exposure to the take/pollEvents/reset lifecycle.
     * ====================================================================== */

    // TODO 1.1 — create a method called ac1_runWatchLoop


    private static void activity1() throws IOException, InterruptedException {
        System.out.println("\n=== Activity 1: Register a Watch, See Events ===");

        // TODO 1.2 — uncomment the body below
        // Path watched = freshActivityDir("activity1");
        // System.out.println("  watching: " + watched);
        //
        // try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
        //     watched.register(watcher,
        //             StandardWatchEventKinds.ENTRY_CREATE,
        //             StandardWatchEventKinds.ENTRY_MODIFY,
        //             StandardWatchEventKinds.ENTRY_DELETE);
        //
        //     running.set(true);
        //     Thread helper = new Thread(() -> ac1_helperThread(watched), "ac1-helper");
        //     helper.setDaemon(true);
        //     helper.start();
        //
        //     // Run the watch loop for ~2 seconds, enough for all three helper actions.
        //     long deadline = System.currentTimeMillis() + 2000;
        //     while (running.get() && System.currentTimeMillis() < deadline) {
        //         ac1_runWatchLoop(watcher, watched);
        //     }
        //
        //     running.set(false);
        //     helper.join(500);
        // }
    }


    /* ========================================================================
     *  Activity 2: You Watch Directories, Not Files
     *  -------------------------------------------------------------------
     *  Part A: try to register a WatchService on a file path, observe
     *          NotDirectoryException.
     *  Part B: re-register on the parent directory and filter events by
     *          filename to achieve per-file watching.
     * ====================================================================== */

    // TODO 2.1 — create a method called ac2_tryWatchFile


    // TODO 2.2 — create a method called ac2_watchOneFile


    private static void activity2() throws IOException, InterruptedException {
        System.out.println("\n=== Activity 2: You Watch Directories, Not Files ===");

        // TODO 2.3 — uncomment the body below
        // Path watched = freshActivityDir("activity2");
        // Path target  = watched.resolve("target.txt");
        // Files.writeString(target, "initial");
        //
        // System.out.println("\n--- Part A: try to watch a file directly ---");
        // ac2_tryWatchFile(target);
        //
        // System.out.println("\n--- Part B: watch the parent, filter by filename ---");
        // running.set(true);
        // ac2_watchOneFile(target);
        // running.set(false);
    }


    /* ========================================================================
     *  Activity 3: Platform Semantics, One Save, Many Events
     *  -------------------------------------------------------------------
     *  Phase A: naive handler prints every ENTRY_MODIFY the OS delivers.
     *           One logical save often fires two or three events.
     *  Phase B: debounced handler collapses events for the same file inside
     *           a 150ms window using a Map<Path, Instant>.
     * ====================================================================== */

    // TODO 3.1 — create a method called ac3_runNaiveLoop


    // TODO 3.2 — create a method called ac3_runDebouncedLoop


    /**
     * Helper thread body for Activity 3. Writes to the target file three
     * times with short pauses between writes. Each Files.writeString on
     * most platforms generates more than one ENTRY_MODIFY event (content
     * write plus metadata update), which is exactly what the naive
     * handler will expose and the debounced handler will collapse.
     */
    private static void ac3_helperThread(Path target) {
        try {
            sleep(250);
            Files.writeString(target, "save-1");
            sleep(400);
            Files.writeString(target, "save-2");
            sleep(400);
            Files.writeString(target, "save-3");
        } catch (IOException ignored) {}
    }

    private static void activity3() throws IOException, InterruptedException {
        System.out.println("\n=== Activity 3: Platform Semantics, One Save, Many Events ===");

        // TODO 3.3 — uncomment the body below
        // Path watched = freshActivityDir("activity3");
        // Path target  = watched.resolve("config.yaml");
        // Files.writeString(target, "initial");
        //
        // // ----- Phase A: naive handler -----
        // System.out.println("\n--- Phase A: naive handler ---");
        // try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
        //     watched.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
        //
        //     running.set(true);
        //     Thread helperA = new Thread(() -> ac3_helperThread(target), "ac3-helper-naive");
        //     helperA.setDaemon(true);
        //     helperA.start();
        //
        //     long deadlineA = System.currentTimeMillis() + 2000;
        //     ac3_runNaiveLoop(watcher, deadlineA);
        //
        //     running.set(false);
        //     helperA.join(500);
        // }
        //
        // // ----- Phase B: debounced handler -----
        // System.out.println("\n--- Phase B: debounced handler (150ms window) ---");
        // try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
        //     watched.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
        //
        //     running.set(true);
        //     Thread helperB = new Thread(() -> ac3_helperThread(target), "ac3-helper-debounced");
        //     helperB.setDaemon(true);
        //     helperB.start();
        //
        //     long deadlineB = System.currentTimeMillis() + 2000;
        //     ac3_runDebouncedLoop(watcher, deadlineB, 150);
        //
        //     running.set(false);
        //     helperB.join(500);
        // }
    }


    /* ========================================================================
     *  Menu
     * ====================================================================== */

    public static void main(String[] args) throws IOException, InterruptedException {
        Files.createDirectories(WORK_DIR);
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n----------------------------------------");
                System.out.println(" Lab 3.3 — WatchService & Filesystem Events");
                System.out.println("----------------------------------------");
                System.out.println(" 1) Register a Watch, See Events");
                System.out.println(" 2) You Watch Directories, Not Files");
                System.out.println(" 3) Platform Semantics, One Save, Many Events");
                System.out.println(" q) Quit");
                System.out.print  (" > ");

                String choice = in.hasNextLine() ? in.nextLine().trim() : "q";

                switch (choice) {
                    // TODO 1.2 — uncomment the case below when Activity 1 is implemented
                    // case "1" -> activity1();

                    // TODO 2.3 — uncomment the case below when Activity 2 is implemented
                    // case "2" -> activity2();

                    // TODO 3.3 — uncomment the case below when Activity 3 is implemented
                    // case "3" -> activity3();

                    case "q", "Q" -> { cleanup(); return; }
                    default       -> System.out.println("  unknown choice: " + choice);
                }
            }
        }
    }

    /** Best-effort cleanup of the working directory on exit. */
    private static void cleanup() {
        running.set(false);
        try {
            if (Files.exists(WORK_DIR)) {
                try (var stream = Files.walk(WORK_DIR)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                          .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {}
    }
}

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Lab_3_2_ModernFileSystem {

    /* ---------- Shared scaffolding ---------- */

    // Working directory for all activities. Created fresh per activity so runs
    // don't see state from earlier attempts. Lives under the system temp dir,
    // nothing is committed to the repo.
    private static final Path WORK_DIR = Path.of(
            System.getProperty("java.io.tmpdir"), "lab_3_2_nio2");

    /**
     * Wipes and recreates WORK_DIR. Called at the start of every activity so
     * each run starts from a known-clean state.
     */
    private static void resetWorkDir() throws IOException {
        if (Files.exists(WORK_DIR)) {
            try (Stream<Path> stream = Files.walk(WORK_DIR)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(WORK_DIR);
    }

    /**
     * Creates a single file containing the given text. Used by Activities 1
     * and 2 to set up specific failure scenarios.
     */
    private static Path writeFile(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return path;
    }

    /**
     * Builds a small directory tree under WORK_DIR for Activities 3 and 4:
     *
     *   tree/
     *     a.txt               (small)
     *     b.log               (small)
     *     sub1/
     *       c.txt             (medium)
     *       sub1a/
     *         d.txt           (large)
     *     sub2/
     *       e.log             (large)
     *
     * Sizes are staggered so the size-threshold filter in Activity 4 has a
     * meaningful result.
     */
    private static Path ac3_buildTree() throws IOException {
        Path root = WORK_DIR.resolve("tree");
        Files.createDirectories(root.resolve("sub1/sub1a"));
        Files.createDirectories(root.resolve("sub2"));

        writeFile(root.resolve("a.txt"),              "a");                   // 1 byte
        writeFile(root.resolve("b.log"),              "bb");                  // 2 bytes
        writeFile(root.resolve("sub1/c.txt"),         "cccccccccc");          // 10 bytes
        writeFile(root.resolve("sub1/sub1a/d.txt"),   "d".repeat(500));       // 500 bytes
        writeFile(root.resolve("sub2/e.log"),         "e".repeat(500));       // 500 bytes
        return root;
    }


    /* ========================================================================
     *  Activity 1: File.delete() vs Files.delete(Path)
     *  -------------------------------------------------------------------
     *  Same failure (missing file, non-empty directory). File returns a
     *  boolean, Files throws a typed exception carrying the path.
     * ====================================================================== */

    // TODO 1.1 — create a method called ac1_deleteWithFile


    // TODO 1.2 — create a method called ac1_deleteWithFiles


    private static void activity1() throws IOException {
        System.out.println("\n=== Activity 1: File.delete() vs Files.delete(Path) ===");
        resetWorkDir();

        Path missing    = WORK_DIR.resolve("does_not_exist.txt");
        Path nonEmptyDir = WORK_DIR.resolve("not_empty");
        Files.createDirectories(nonEmptyDir);
        writeFile(nonEmptyDir.resolve("child.txt"), "content");

        // TODO 1.3 — uncomment the four call lines below
        // System.out.println("\n--- Part A: File.delete() on a missing file ---");
        // ac1_deleteWithFile(missing);
        //
        // System.out.println("\n--- Part B: Files.delete() on the same missing file ---");
        // ac1_deleteWithFiles(missing);
        //
        // System.out.println("\n--- Part C: Files.delete() on a non-empty directory ---");
        // ac1_deleteWithFiles(nonEmptyDir);
    }


    /* ========================================================================
     *  Activity 2: renameTo vs Files.move
     *  -------------------------------------------------------------------
     *  renameTo is a single boolean. Files.move is typed exceptions.
     *  ATOMIC_MOVE promises atomicity or throws, so you learn at the call
     *  site whether the platform can honour your guarantee.
     * ====================================================================== */

    // TODO 2.1 — create a method called ac2_renameWithFile


    // TODO 2.2 — create a method called ac2_moveWithFiles


    // TODO 2.3 — create a method called ac2_moveAtomic


    private static void activity2() throws IOException {
        System.out.println("\n=== Activity 2: renameTo vs Files.move ===");
        resetWorkDir();

        // TODO 2.4 — uncomment the body below
        // Path srcA = writeFile(WORK_DIR.resolve("a_source.txt"), "part A");
        // Path dstA = WORK_DIR.resolve("a_renamed.txt");
        //
        // Path srcB = writeFile(WORK_DIR.resolve("b_source.txt"), "part B");
        // Path dstB = WORK_DIR.resolve("b_moved.txt");
        //
        // Path srcC = writeFile(WORK_DIR.resolve("c_source.txt"), "part C");
        // Path dstC = WORK_DIR.resolve("c_atomic_same_fs.txt");
        //
        // Path srcD = writeFile(WORK_DIR.resolve("d_source.txt"), "part D");
        // Path dstD = Path.of(System.getProperty("java.io.tmpdir")).resolve("d_atomic_outside.txt");
        //
        // System.out.println("\n--- Part A: File.renameTo within the same directory ---");
        // ac2_renameWithFile(srcA, dstA);
        //
        // System.out.println("\n--- Part B: Files.move with no options ---");
        // ac2_moveWithFiles(srcB, dstB);
        //
        // System.out.println("\n--- Part C: Files.move with ATOMIC_MOVE, same filesystem ---");
        // ac2_moveAtomic(srcC, dstC);
        //
        // System.out.println("\n--- Part D: Files.move with ATOMIC_MOVE, possibly different filesystem ---");
        // ac2_moveAtomic(srcD, dstD);
        // try { Files.deleteIfExists(dstD); } catch (IOException ignored) {}
    }


    /* ========================================================================
     *  Activity 3: Walking a tree with Files.walk
     *  -------------------------------------------------------------------
     *  Hand-rolled File.listFiles recursion vs one Files.walk stream.
     *  Same answer, dramatically less code, composes with everything else
     *  in the Streams API.
     * ====================================================================== */

    // TODO 3.1 — create a method called ac3_walkLegacy


    // TODO 3.2 — create a method called ac3_walkNio


    private static void activity3() throws IOException {
        System.out.println("\n=== Activity 3: Walking a tree with Files.walk ===");
        resetWorkDir();
        Path root = ac3_buildTree();

        // TODO 3.3 — uncomment the two call groups below
        // System.out.println("\n--- Part A: legacy File.listFiles recursion ---");
        // List<java.io.File> legacy = ac3_walkLegacy(root.toFile());
        // System.out.println("  files found (legacy): " + legacy.size());
        // legacy.forEach(f -> System.out.println("    " + f.getName()));
        //
        // System.out.println("\n--- Part B: Files.walk stream ---");
        // List<Path> nio = ac3_walkNio(root);
        // System.out.println("  files found (NIO):    " + nio.size());
        // nio.forEach(p -> System.out.println("    " + p.getFileName()));
    }


    /* ========================================================================
     *  Activity 4: Files.find with a BiPredicate
     *  -------------------------------------------------------------------
     *  walk().filter(p -> Files.size(p)...) issues two stats per file.
     *  find(root, depth, (p, attrs) -> ...) issues one. Same result, half
     *  the syscalls.
     * ====================================================================== */

    // TODO 4.1 — create a method called ac4_findWithWalk


    // TODO 4.2 — create a method called ac4_findWithFind


    private static void activity4() throws IOException {
        System.out.println("\n=== Activity 4: Files.find with a BiPredicate ===");
        resetWorkDir();
        Path root = ac3_buildTree();
        long threshold = 100;                           // bytes

        // TODO 4.3 — uncomment the body below
        // System.out.println("\n--- Part A: Files.walk + filter on Files.size ---");
        // long[] walkResult = ac4_findWithWalk(root, threshold);
        // System.out.println("  matches:                 " + walkResult[0]);
        // System.out.println("  Files.size() invocations: " + walkResult[1]);
        //
        // System.out.println("\n--- Part B: Files.find with BiPredicate<Path, BasicFileAttributes> ---");
        // long[] findResult = ac4_findWithFind(root, threshold);
        // System.out.println("  matches:                 " + findResult[0]);
        // System.out.println("  predicate invocations:   " + findResult[1]);
        //
        // System.out.println("\n  walk visited " + walkResult[1] + " candidates and stat-ed each one again for size.");
        // System.out.println("  find visited " + findResult[1] + " candidates using attributes already in hand.");
    }


    /* ========================================================================
     *  Menu
     * ====================================================================== */

    public static void main(String[] args) throws IOException {
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n----------------------------------------");
                System.out.println(" Lab 3.2 — Modern File System API (NIO.2)");
                System.out.println("----------------------------------------");
                System.out.println(" 1) File.delete() vs Files.delete(Path)");
                System.out.println(" 2) renameTo vs Files.move");
                System.out.println(" 3) Walking a tree with Files.walk");
                System.out.println(" 4) Files.find with a BiPredicate");
                System.out.println(" q) Quit");
                System.out.print  (" > ");

                String choice = in.hasNextLine() ? in.nextLine().trim() : "q";

                switch (choice) {
                    // TODO 1.3 — uncomment the case below when Activity 1 is implemented
                    // case "1" -> activity1();

                    // TODO 2.4 — uncomment the case below when Activity 2 is implemented
                    // case "2" -> activity2();

                    // TODO 3.3 — uncomment the case below when Activity 3 is implemented
                    // case "3" -> activity3();

                    // TODO 4.3 — uncomment the case below when Activity 4 is implemented
                    // case "4" -> activity4();

                    case "q", "Q" -> { cleanup(); return; }
                    default       -> System.out.println("  unknown choice: " + choice);
                }
            }
        }
    }

    /** Best-effort cleanup of the working directory on exit. */
    private static void cleanup() {
        try {
            if (Files.exists(WORK_DIR)) {
                try (Stream<Path> stream = Files.walk(WORK_DIR)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                          .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {}
    }
}

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.Scanner;

public class Lab_3_1_ChannelsAndBuffers {

    /* ---------- Shared scaffolding ---------- */

    // Short sample for Activity 1 so buffer state dumps stay readable.
    private static final String SAMPLE_TEXT = "Hello NIO";

    // 50MB - large enough for timing differences to be obvious, small enough
    // to complete in under a few seconds on any laptop.
    private static final int LARGE_FILE_SIZE = 50 * 1024 * 1024;

    // Working directory for Activities 2 and 3. Created on demand.
    private static final Path WORK_DIR = Path.of(
            System.getProperty("java.io.tmpdir"), "lab_3_1_channels");

    /**
     * Prints the three cursors of a ByteBuffer with a label so students can
     * see the state machine move as operations are applied.
     */
    private static void printBufferState(String label, ByteBuffer buffer) {
        System.out.printf("  %-18s position=%-4d limit=%-4d capacity=%-4d%n",
                label, buffer.position(), buffer.limit(), buffer.capacity());
    }

    /**
     * Renders the first {@code len} bytes of {@code bytes} as space-separated
     * two-digit hex values. Used in Activity 1 to make null bytes visible in
     * the console output.
     */
    private static String toHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    /**
     * Generates a file of LARGE_FILE_SIZE bytes of random data using a
     * FileChannel and a direct buffer. Used by Activities 2 and 3 as the
     * source for copy benchmarks.
     */
    private static void ac2_generateLargeFile(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (FileChannel out = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buf = ByteBuffer.allocateDirect(64 * 1024);
            Random random = new Random(42);
            int written = 0;
            byte[] chunk = new byte[buf.capacity()];

            while (written < LARGE_FILE_SIZE) {
                random.nextBytes(chunk);
                int toWrite = Math.min(chunk.length, LARGE_FILE_SIZE - written);
                buf.clear();
                buf.put(chunk, 0, toWrite);
                buf.flip();
                out.write(buf);
                written += toWrite;
            }
        }
    }

    /*
     * ========================================================================
     * Activity 1: The Buffer Dance
     * -------------------------------------------------------------------
     * Demonstrate the ByteBuffer state machine by writing then reading
     * without .flip() (Part A) and with .flip() (Part B). The difference
     * is zero bytes read vs all bytes read.
     * ======================================================================
     */

    // TODO 1.1 — create a method called ac1_writeWithoutFlip
    private static void ac1_writeWithoutFlip() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        printBufferState("after allocate", buffer);

        buffer.put(SAMPLE_TEXT.getBytes());
        printBufferState("after put", buffer);

        byte[] out = new byte[SAMPLE_TEXT.length()];
        int read = 0;
        while (buffer.hasRemaining() && read < out.length) {
            out[read++] = buffer.get();
        }
        printBufferState("after get", buffer);
        System.out.println("  bytes read:      " + read);
        System.out.println("  raw bytes (hex): " + toHex(out, read));
        System.out.println("  content:         \"" + new String(out, 0, read) + "\"");
    }

    // TODO 1.2 — create a method called ac1_writeWithFlip
    private static void ac1_writeWithFlip() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        printBufferState("after allocate", buffer);

        buffer.put(SAMPLE_TEXT.getBytes());
        printBufferState("after put", buffer);

        buffer.flip();
        printBufferState("after flip", buffer);

        byte[] out = new byte[SAMPLE_TEXT.length()];
        int read = 0;
        while (buffer.hasRemaining() && read < out.length) {
            out[read++] = buffer.get();
        }
        printBufferState("after get", buffer);
        System.out.println("  bytes read:      " + read);
        System.out.println("  raw bytes (hex): " + toHex(out, read));
        System.out.println("  content:         \"" + new String(out, 0, read) + "\"");
    }

    private static void activity1() {
        System.out.println("\n=== Activity 1: The Buffer Dance ===");

        System.out.println("\n--- Part A: put without flip ---");
        // TODO 1.3 — uncomment the two call lines below
        ac1_writeWithoutFlip();

        System.out.println("\n--- Part B: put, flip, get ---");
        ac1_writeWithFlip();
    }

    /*
     * ========================================================================
     * Activity 2: Stream I/O vs Channel Throughput
     * -------------------------------------------------------------------
     * Copy the same 50MB file two ways and time both. Same bytes moved,
     * very different wall-clock times.
     * ======================================================================
     */

    // TODO 2.1 — create a method called ac2_copyWithStreams
        private static long ac2_copyWithStreams(Path src, Path dst) throws IOException {
        long start = System.nanoTime();
        try (FileInputStream  in  = new FileInputStream(src.toFile());
             FileOutputStream out = new FileOutputStream(dst.toFile())) {
            byte[] buf = new byte[4 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // TODO 2.2 — create a method called ac2_copyWithChannels
        private static long ac2_copyWithChannels(Path src, Path dst) throws IOException {
        long start = System.nanoTime();
        try (FileChannel in  = FileChannel.open(src, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(dst, StandardOpenOption.CREATE,
                                                     StandardOpenOption.WRITE,
                                                     StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.allocateDirect(64 * 1024);
            while (in.read(buf) != -1) {
                buf.flip();
                out.write(buf);
                buf.clear();
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void activity2() throws IOException {
        System.out.println("\n=== Activity 2: Stream I/O vs Channel Throughput ===");

        // TODO 2.3 — uncomment the body below
        Path source = WORK_DIR.resolve("source.bin");
        Path dstStreams = WORK_DIR.resolve("dst_streams.bin");
        Path dstChannels = WORK_DIR.resolve("dst_channels.bin");
        
        System.out.println(" generating " + (LARGE_FILE_SIZE / (1024 * 1024)) + "MB source file...");
        ac2_generateLargeFile(source);
        System.out.println(" source size: " + Files.size(source) + " bytes");
        
        long streamMs = ac2_copyWithStreams(source, dstStreams);
        long channelMs = ac2_copyWithChannels(source, dstChannels);
        
        System.out.println("\n FileInputStream/FileOutputStream (4KB buffer): " +
        streamMs + " ms");
        System.out.println(" FileChannel + 64KB direct ByteBuffer: " + channelMs + "ms");
        System.out.printf (" channel speedup: %.2fx%n",
        (double) streamMs / channelMs);
        
        // Correctness check: both copies should produce identical file sizes.
        System.out.println("\n size check streams: " + Files.size(dstStreams));
        System.out.println(" size check channels: " + Files.size(dstChannels));
    }

    /*
     * ========================================================================
     * Activity 3: Zero-Copy with transferTo
     * -------------------------------------------------------------------
     * Replace the read-flip-write-clear loop with a single transferTo
     * call. No user-space buffer. Kernel moves the bytes directly.
     * ======================================================================
     */

    // TODO 3.1 — create a method called ac3_copyWithTransferTo

    private static void activity3() throws IOException {
        System.out.println("\n=== Activity 3: Zero-Copy with transferTo ===");

        // TODO 3.2 — uncomment the body below
        // Path source = WORK_DIR.resolve("source.bin");
        // Path dstChannels = WORK_DIR.resolve("dst_channels.bin");
        // Path dstTransfer = WORK_DIR.resolve("dst_transfer.bin");
        //
        // // Regenerate the source if Activity 2 wasn't run in this session.
        // if (!Files.exists(source) || Files.size(source) != LARGE_FILE_SIZE) {
        // System.out.println(" generating " + (LARGE_FILE_SIZE / (1024 * 1024)) + "MB
        // source file...");
        // ac2_generateLargeFile(source);
        // }
        //
        // // Run Activity 2's channel copy as the baseline for comparison.
        // long channelMs = ac2_copyWithChannels(source, dstChannels);
        // long transferMs = ac3_copyWithTransferTo(source, dstTransfer);
        //
        // System.out.println("\n FileChannel + 64KB direct ByteBuffer loop: " +
        // channelMs + " ms");
        // System.out.println(" FileChannel.transferTo (kernel-level copy): " +
        // transferMs + " ms");
        // System.out.printf (" transferTo speedup over buffered channels: %.2fx%n",
        // (double) channelMs / Math.max(transferMs, 1));
        //
        // System.out.println("\n size check channels: " + Files.size(dstChannels));
        // System.out.println(" size check transfer: " + Files.size(dstTransfer));
    }

    /*
     * ========================================================================
     * Menu
     * ======================================================================
     */

    public static void main(String[] args) throws IOException {
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n----------------------------------------");
                System.out.println(" Lab 3.1 — Channels & Buffers");
                System.out.println("----------------------------------------");
                System.out.println(" 1) The Buffer Dance");
                System.out.println(" 2) Stream I/O vs Channel Throughput");
                System.out.println(" 3) Zero-Copy with transferTo");
                System.out.println(" q) Quit");
                System.out.print(" > ");

                String choice = in.hasNextLine() ? in.nextLine().trim() : "q";

                switch (choice) {
                    // TODO 1.3 — uncomment the case below when Activity 1 is implemented
                    case "1" -> activity1();

                    // TODO 2.3 — uncomment the case below when Activity 2 is implemented
                    case "2" -> activity2();

                    // TODO 3.2 — uncomment the case below when Activity 3 is implemented
                    // case "3" -> activity3();

                    case "q", "Q" -> {
                        cleanup();
                        return;
                    }
                    default -> System.out.println("  unknown choice: " + choice);
                }
            }
        }
    }

    /** Best-effort cleanup of the working directory on exit. */
    private static void cleanup() {
        try {
            if (Files.exists(WORK_DIR)) {
                try (var stream = Files.walk(WORK_DIR)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException ignored) {
        }
    }
}

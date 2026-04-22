# Lab 3.1 — Channels & Buffers

**Module:** 3 — Java NIO
**Files:** `Lab_3_1_ChannelsAndBuffers.java`

---

## Activity 1: The Buffer Dance

*The Trap* — `ByteBuffer` looks like an array with extra methods. You allocate it, write to it, read from it. Done. The API even reads in a natural order: `put`, then `get`. So developers write `put` followed by `get` and are genuinely surprised when `get` returns zero bytes, throws `BufferUnderflowException`, or silently reads garbage past their data.

The problem is that `ByteBuffer` is a state machine, not an array. It carries three cursors: `position`, `limit`, and `capacity`. Writing advances `position`. Reading also reads from `position`. So after you finish writing, `position` points to the end of your data, and reads from that point keep walking forward into the unwritten portion of the buffer. What they find there is whatever happened to be in memory when the buffer was allocated (zeros on a freshly allocated heap buffer), not the text you just wrote.

`.flip()` is the reset between those two phases. It sets `limit = position` (the end of what you wrote) and `position = 0` (the start of what you want to read). One method call, two cursor moves, and suddenly the read sees your data. Miss the flip and everything compiles, runs, and returns garbage.

### Steps

1. Open `Lab_3_1_ChannelsAndBuffers.java`.

2. Read the scaffolding at the top of the file. Note the `SAMPLE_TEXT` constant and the `printBufferState` helper that dumps `position`, `limit`, and `capacity` for any buffer.

3. Locate **TODO 1.1**.

    Create a method called `ac1_writeWithoutFlip` that allocates a `ByteBuffer` of capacity 64, writes `SAMPLE_TEXT` into it as bytes, then immediately tries to read bytes back into a fresh `byte[]`. Print the buffer state before and after each operation, and print what was read.

4. Paste this snippet:

```java
    private static void ac1_writeWithoutFlip() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        printBufferState("after allocate", buffer);

        buffer.put(SAMPLE_TEXT.getBytes());
        printBufferState("after put",      buffer);

        byte[] out = new byte[SAMPLE_TEXT.length()];
        int read = 0;
        while (buffer.hasRemaining() && read < out.length) {
            out[read++] = buffer.get();
        }
        printBufferState("after get",      buffer);
        System.out.println("  bytes read:      " + read);
        System.out.println("  raw bytes (hex): " + toHex(out, read));
        System.out.println("  content:         \"" + new String(out, 0, read) + "\"");
    }
```

5. Locate **TODO 1.2**.

    Create a method called `ac1_writeWithFlip` that does the same operations, but calls `.flip()` between the put and the get.

6. Paste this snippet:

```java
    private static void ac1_writeWithFlip() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        printBufferState("after allocate", buffer);

        buffer.put(SAMPLE_TEXT.getBytes());
        printBufferState("after put",      buffer);

        buffer.flip();
        printBufferState("after flip",     buffer);

        byte[] out = new byte[SAMPLE_TEXT.length()];
        int read = 0;
        while (buffer.hasRemaining() && read < out.length) {
            out[read++] = buffer.get();
        }
        printBufferState("after get",      buffer);
        System.out.println("  bytes read:      " + read);
        System.out.println("  raw bytes (hex): " + toHex(out, read));
        System.out.println("  content:         \"" + new String(out, 0, read) + "\"");
    }
```

7. Locate **TODO 1.3**.

8. Uncomment the two call lines inside `activity1()`.

9. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

10. Compile and run `Lab_3_1_ChannelsAndBuffers`.

11. Select **1** from the menu.

12. Observe:
    - Part A reads bytes, but not the ones we wrote. The `raw bytes (hex)` line shows `00 00 00 00 00 00 00 00 00`, nine null bytes. After `put`, `position` sits past the written data. The read loop keeps going forward into the unwritten portion of the buffer and finds zeros. No exception, no warning, just wrong data.
    - Part B reads the full text. The hex line now shows the ASCII codes of "Hello NIO" (`48 65 6c 6c 6f 20 4e 49 4f`). `.flip()` moved `limit` to where `position` was, then reset `position` to zero. The `get` loop now walks exactly the written bytes and stops.
    - The buffer state dumps are the proof. Compare the "after put" line in Part A against the "after flip" line in Part B, and the two cursor moves that `.flip()` performs become visible.
    - **Question:** `.flip()` sets `limit = position` and `position = 0`. If you called `.flip()` twice in a row, what would happen to the cursors, and what would the next `get()` see? Work it through on paper before running it.

---

## Activity 2: Stream I/O vs Channel Throughput

*The Hidden Cost* — `FileInputStream` and `FileOutputStream` have been in Java since 1.0. They work. They still work. For a configuration file or a log line they are perfectly adequate. But every byte that moves through them crosses the JVM-to-OS boundary one `int` at a time in the worst case, or a few kilobytes at a time in the best case. At scale that adds up to seconds.

`FileChannel` moves bytes in larger chunks through a `ByteBuffer`. Fewer syscalls, fewer JNI crossings, and the buffer can be tuned to match the filesystem's natural block size. The API is harder to use (you have to manage the buffer state machine from Activity 1) but the throughput difference on large files is not subtle.

This activity copies the same file two ways and times both. The numbers are the lesson. They are also the judgement call: for small files, streams are fine. For files past a few megabytes, channels start to matter. Past a hundred megabytes, channels are the only sensible choice.

### Steps

1. Read the `LARGE_FILE_SIZE` constant near the top of the file. It is set to 50MB. The file is generated fresh at the start of each activity run under the system temp directory, so nothing needs to be committed to the repo.

2. Read the `ac2_generateLargeFile` helper. It writes random bytes using a `FileChannel` and a direct buffer. Note that generating the file is itself a channel operation, reinforcing Activity 1.

3. Locate **TODO 2.1**.

    Create a method called `ac2_copyWithStreams` that takes a source `Path` and a destination `Path`, opens `FileInputStream` and `FileOutputStream`, and copies bytes using a 4KB `byte[]` buffer in a read-write loop. Return the elapsed time in milliseconds.

4. Paste this snippet:

```java
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
```

5. Locate **TODO 2.2**.

    Create a method called `ac2_copyWithChannels` that opens `FileChannel`s on both paths and copies bytes using a 64KB direct `ByteBuffer` in a read-flip-write-clear loop. Return the elapsed time in milliseconds.

6. Paste this snippet:

```java
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
```

7. Locate **TODO 2.3**.

8. Uncomment the `activity2()` body (the generate, copy, and print calls).

9. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

10. Compile and run. Select **2**.

11. Observe:
    - Both copies produce the same number of bytes on disk. The correctness check at the end of `activity2()` compares file sizes to confirm.
    - The channel copy is typically 2x to 5x faster on 50MB. The exact ratio depends on disk speed, filesystem, and JVM version, but channels win consistently.
    - The stream copy's 4KB buffer means ~12,800 read calls and ~12,800 write calls for 50MB. The channel copy's 64KB buffer means ~800 of each. Fewer syscalls is most of the speedup.
    - The `flip/write/clear` sequence in the channel loop is the Activity 1 state machine in production form. `flip` prepares the buffer for reading (writing to the destination channel), `clear` prepares it for writing again (reading from the source channel). Forgetting either is a silent bug.
    - **Question:** The stream copy uses a 4KB buffer, the channel copy uses a 64KB buffer. If you gave the stream copy a 64KB buffer as well, would it match the channel copy's throughput? What other factors, beyond buffer size, are in play?

---

## Activity 3: Zero-Copy with `transferTo`

*The Illusion* — Activity 2 made channel code look like this: allocate a buffer, loop reading from one channel into the buffer, flip, write the buffer to the other channel, clear, repeat. That pattern works and it is faster than streams. It is also unnecessary.

Every byte in that loop makes two trips across the JVM-to-OS boundary: kernel reads disk into its page cache, copies to the user-space `ByteBuffer`, user-space code writes the buffer, kernel copies from the buffer back into a different page cache, kernel writes to disk. The buffer round-trip is pure overhead. The bytes did not need to visit user space at all.

`FileChannel.transferTo()` tells the kernel to move bytes directly from one file descriptor to another. No user-space buffer, no copy-in-copy-out, no Java code in the inner loop. On Linux it maps to `sendfile`. On Windows it maps to `TransmitFile`. The same primitive powers every high-throughput file server, reverse proxy, and static asset CDN you have used today.

One line of code replaces the entire read-flip-write-clear loop. The file copies, and the throughput measurement tells the rest.

### Steps

1. Locate **TODO 3.1**.

    Create a method called `ac3_copyWithTransferTo` that takes source and destination `Path`s, opens a `FileChannel` on each, and calls `transferTo` in a loop until all bytes are moved. Return the elapsed time in milliseconds.

2. Paste this snippet:

```java
    private static long ac3_copyWithTransferTo(Path src, Path dst) throws IOException {
        long start = System.nanoTime();
        try (FileChannel in  = FileChannel.open(src, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(dst, StandardOpenOption.CREATE,
                                                     StandardOpenOption.WRITE,
                                                     StandardOpenOption.TRUNCATE_EXISTING)) {
            long size = in.size();
            long transferred = 0;
            while (transferred < size) {
                transferred += in.transferTo(transferred, size - transferred, out);
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
```

3. Locate **TODO 3.2**.

4. Uncomment the `activity3()` body.

5. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

6. Compile and run. Select **3**.

7. Observe:
    - No `ByteBuffer`. No `flip`. No `clear`. The entire Activity 2 inner loop collapsed into one call.
    - `transferTo` is typically 2x to 4x faster than the Activity 2 channel copy on the same file. On top of the stream-to-channel speedup, this is a second multiplier.
    - The `while (transferred < size)` loop exists because `transferTo` is allowed to move fewer bytes than requested. Most platforms move the whole file in one call for a 50MB source, but the contract says "at most N bytes," so the loop is the correct form.
    - The JVM is no longer copying bytes through user space. The `javac` output, the `java` process, and even the `ByteBuffer` class are not in the hot path anymore. The kernel is doing the work.
    - **Question:** `transferTo` requires both endpoints to be channels (specifically, the destination must be a `WritableByteChannel`). The classic production use is not file-to-file, but file-to-socket, where the destination is a `SocketChannel` serving an HTTP response. Why is file-to-socket the case where zero-copy matters most?

---

## Lab Complete

Buffers are state machines. Channels move bytes in bulk. `transferTo` removes user space from the equation entirely. The three activities form a ladder: each one is faster than the last, and each one requires the discipline of the one before it. The same `ByteBuffer` you wrestled with in Activity 1 is invisible in Activity 3, not because it went away, but because the kernel took over its job.

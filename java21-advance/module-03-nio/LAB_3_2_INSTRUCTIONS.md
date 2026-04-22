# Lab 3.2 — Modern File System API (NIO.2)

**Module:** 3 — Java NIO
**Files:** `Lab_3_2_ModernFileSystem.java`

---

## Activity 1: `File.delete()` vs `Files.delete(Path)`

*The False Promise* — `java.io.File` has been in Java since 1.0, and its delete method has the cleanest signature imaginable: `boolean delete()`. One line, one return value. The developer writes `if (!file.delete()) { log.warn("could not delete"); }` and moves on. The code compiles, runs, and in production it silently papers over bugs for years.

The problem is that `File.delete()` returns `false` for every possible failure mode and tells you nothing about which one you hit. The file did not exist. The file existed but you lacked permission. The file was locked by another process. The directory was not empty. The JVM does not know, the OS does not say, and your log line reads "could not delete" with no context to act on. If you wanted to know *why*, you have to probe the filesystem yourself with more `File` calls, each one of which is a separate syscall and a separate race condition.

`Files.delete(Path)` from NIO.2 throws a specific, typed exception for each failure mode. `NoSuchFileException` when the file is missing. `DirectoryNotEmptyException` when the target is a non-empty directory. `AccessDeniedException` when permissions block you. The exception carries the path. You can catch the one you expect and let the others propagate. The signature is worse (it throws `IOException`), but the information content is dramatically better, and "worse signature, better information" is the trade NIO.2 asks you to make throughout the API.

### Steps

1. Open `Lab_3_2_ModernFileSystem.java`.

2. Read the scaffolding at the top of the file. Note the `WORK_DIR` constant (a temp directory created fresh at the start of each activity) and the `setupWorkDir` / `resetWorkDir` helpers.

3. Locate **TODO 1.1**.

    Create a method called `ac1_deleteWithFile` that takes a `Path`, converts it to `java.io.File`, calls `.delete()`, and prints the boolean result. Catch nothing, this call does not throw.

4. Paste this snippet:

```java
    private static void ac1_deleteWithFile(Path path) {
        java.io.File legacy = path.toFile();
        boolean result = legacy.delete();
        System.out.println("  File.delete() on \"" + path.getFileName() + "\" returned: " + result);
    }
```

5. Locate **TODO 1.2**.

    Create a method called `ac1_deleteWithFiles` that takes a `Path` and calls `Files.delete(path)`. Catch `NoSuchFileException`, `DirectoryNotEmptyException`, and `IOException` separately, and print the exception type plus the path it carries.

6. Paste this snippet:

```java
    private static void ac1_deleteWithFiles(Path path) {
        try {
            Files.delete(path);
            System.out.println("  Files.delete() on \"" + path.getFileName() + "\" succeeded");
        } catch (NoSuchFileException e) {
            System.out.println("  NoSuchFileException: " + e.getFile());
        } catch (DirectoryNotEmptyException e) {
            System.out.println("  DirectoryNotEmptyException: " + e.getFile());
        } catch (IOException e) {
            System.out.println("  IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
```

7. Locate **TODO 1.3**.

8. Uncomment the four call lines inside `activity1()`.

9. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

10. Compile and run `Lab_3_2_ModernFileSystem`.

11. Select **1** from the menu.

12. Observe:
    - Part A calls `File.delete()` on a missing file. Returns `false`. No exception, no stack trace, no indication of *why* it failed. If this were production code wrapped in `if (!file.delete())`, you would log a warning and carry on, never learning that the file was absent rather than locked.
    - Part B calls `Files.delete()` on the same missing file. `NoSuchFileException` fires with the exact path attached. Now you can catch the "missing" case specifically and react differently from the "locked" case or the "permission denied" case.
    - Part C calls `Files.delete()` on a non-empty directory. `DirectoryNotEmptyException` fires, also with the path. `File.delete()` would have returned `false` here too, indistinguishable from the missing-file case.
    - The payoff is not just the exception types. It is that each one carries the `Path`, so a generic handler can log *which* file, not just *that* something failed. Legacy `File` code cannot give you that without extra calls.
    - **Question:** Why did the original `java.io.File` API designers return `boolean` instead of throwing? What was reasonable in 1996 that stopped being reasonable by the time NIO.2 shipped in Java 7?

---

## Activity 2: `renameTo` vs `Files.move`

*The Ambiguity* — `File.renameTo(File dest)` returns a `boolean`. Same problem as Activity 1's delete, one bit of information for every possible failure. But rename has a second, nastier property: its *behaviour* is undocumented and platform-specific. 

On the same filesystem, renameTo is atomic. Across filesystems, it is not implemented at all on some platforms, or it silently degrades to a copy-then-delete on others, or it just returns false and leaves both files in place. The Javadoc says "Many aspects of the behavior of this method are inherently platform-dependent," which is a polite way of saying "we give up."

`Files.move(Path, Path, CopyOption...)` makes the contract explicit by making you choose. With no options, it moves by whatever means the platform supports, falling back to copy-then-delete across filesystem boundaries. 

With `StandardCopyOption.ATOMIC_MOVE`, it promises atomicity or throws `AtomicMoveNotSupportedException`. The throw is the point: you asked for atomicity, the filesystem cannot give it, and you find out at the call site instead of discovering mid-operation that your "atomic rename" was actually a partial copy that crashed halfway through.

The trade-off is real. Atomic moves only work within a single filesystem. Across filesystems you need the fallback, and the fallback is not atomic, so a crash mid-copy leaves both files partially written. There is no universal right answer, only a choice the API now forces you to make explicitly.

### Steps

1. Read the `ac2_setup` helper in the scaffolding. It creates two files in the work directory, one to rename within the same directory (same filesystem) and one for the failure demonstration.

2. Locate **TODO 2.1**.

    Create a method called `ac2_renameWithFile` that takes a source `Path` and a destination `Path`, calls `source.toFile().renameTo(dest.toFile())`, and prints the boolean result.

3. Paste this snippet:

```java
    private static void ac2_renameWithFile(Path src, Path dst) {
        boolean result = src.toFile().renameTo(dst.toFile());
        System.out.println("  File.renameTo(\"" + dst.getFileName() + "\") returned: " + result);
    }
```

4. Locate **TODO 2.2**.

    Create a method called `ac2_moveWithFiles` that takes a source `Path` and a destination `Path`, calls `Files.move(src, dst)` with no copy options, and catches `IOException`. Print success or the exception type plus message.

5. Paste this snippet:

```java
    private static void ac2_moveWithFiles(Path src, Path dst) {
        try {
            Files.move(src, dst);
            System.out.println("  Files.move(\"" + src.getFileName() + "\" -> \"" + dst.getFileName() + "\") succeeded");
        } catch (IOException e) {
            System.out.println("  Files.move failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
```

6. Locate **TODO 2.3**.

    Create a method called `ac2_moveAtomic` that takes a source `Path` and a destination `Path`, calls `Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)`, and catches `AtomicMoveNotSupportedException` separately from `IOException`. Print the outcome in each case.

7. Paste this snippet:

```java
    private static void ac2_moveAtomic(Path src, Path dst) {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("  Files.move(ATOMIC_MOVE) succeeded for \"" + dst.getFileName() + "\"");
        } catch (AtomicMoveNotSupportedException e) {
            System.out.println("  AtomicMoveNotSupportedException: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
```

8. Locate **TODO 2.4**.

9. Uncomment the three call groups inside `activity2()`.

10. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

11. Compile and run. Select **2**.

12. Observe:
    - Part A renames within the same directory using `File.renameTo`. Returns `true`. This is the happy path and the reason developers trust the method despite its history.
    - Part B moves a file with `Files.move` and no options. Succeeds. Same bytes, same destination, but now if it had failed you would have a typed exception instead of a boolean.
    - Part C attempts `Files.move` with `ATOMIC_MOVE` to a target on the *same* filesystem. Succeeds. The platform can honour the guarantee within one filesystem.
    - Part D attempts an atomic move to a target outside the work directory into the system temp root. Depending on the platform and filesystem layout, this may succeed (same filesystem, same guarantee) or throw `AtomicMoveNotSupportedException` (different filesystem, cannot guarantee atomicity). The throw is the informative outcome, it tells you the platform *cannot* give you what you asked for, so you can decide whether to retry without the flag or abort.
    - Legacy `renameTo` would silently return `false` in the cross-filesystem case, indistinguishable from a permissions problem or a locked target. You would have no idea whether to retry, redesign, or escalate.
    - **Question:** Plain `Files.move` without `ATOMIC_MOVE` falls back to copy-then-delete across filesystems. If the JVM crashes between the copy and the delete, what state is the filesystem left in? Why does adding `ATOMIC_MOVE` not fix this, and what does it do instead?

---

## Activity 3: Walking a Tree with `Files.walk`

*The Crack* — Every Java codebase older than about 2014 has a helper method that looks roughly like this: take a `File`, call `.listFiles()`, loop over the result, recurse into directories, collect files into a list. It works. 

It is also wrong in subtle ways. `listFiles()` returns `null` on I/O errors instead of throwing, so forgetting the null check is a `NullPointerException` waiting for a permission-denied directory. The recursion has no built-in depth limit. Symlink cycles are not detected. Every File operation is its own syscall. And the whole thing is imperative, so combining it with any other logic (filter by name, count by extension, map to something else) requires more loops.

`Files.walk(Path)` returns a `Stream<Path>` that walks the tree lazily and depth-first, handles I/O errors by throwing `UncheckedIOException`, and composes cleanly with every operation from Module 2. 

Filter by extension is `.filter(p -> p.toString().endsWith(".txt"))`. Count files is `.count()`. Collect paths is `.collect(toList())`. The hand-rolled recursion disappears, replaced by one `walk` call and whatever terminal operation you need.

The stream is also `try-with-resources` managed. `Files.walk` opens directory handles under the hood, and those handles must be released. Forgetting to close the stream leaks file descriptors until the JVM exits. 

This is the one NIO.2 API that absolutely requires the try-with-resources pattern, and it is easy to miss because most streams in Module 2 did not need it.

### Steps

1. Read the `ac3_buildTree` helper in the scaffolding. It creates a small directory tree under the work directory with a few files and subdirectories for the walk to traverse.

2. Locate **TODO 3.1**.

    Create a method called `ac3_walkLegacy` that takes a `java.io.File` root, recursively lists every file (not directory) under it, and returns a `List<File>`. Use the classic `listFiles()` loop and recursion.

3. Paste this snippet:

```java
    private static List<java.io.File> ac3_walkLegacy(java.io.File dir) {
        List<java.io.File> results = new ArrayList<>();
        java.io.File[] children = dir.listFiles();
        if (children == null) return results;           // null on I/O error or not-a-directory
        for (java.io.File child : children) {
            if (child.isDirectory()) {
                results.addAll(ac3_walkLegacy(child));
            } else {
                results.add(child);
            }
        }
        return results;
    }
```

4. Locate **TODO 3.2**.

    Create a method called `ac3_walkNio` that takes a root `Path` and returns a `List<Path>` of every regular file under it. Use `Files.walk` and a try-with-resources block.

5. Paste this snippet:

```java
    private static List<Path> ac3_walkNio(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }
    }
```

6. Locate **TODO 3.3**.

7. Uncomment the two call groups inside `activity3()`.

8. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

9. Compile and run. Select **3**.

10. Observe:
    - Both walks return the same file count. The legacy version needs a recursive method, a null guard, and an accumulator list. The NIO version needs one stream pipeline.
    - `Files.walk` returns `Path` objects directly. No conversion, no string-munging to build paths, no `new File(parent, child)` concatenation.
    - The `try-with-resources` block is required. Remove it and the JVM will still run, but on Windows especially, directory handles stay open until garbage collection decides to run. Under load that is a resource leak.
    - Once you have a `Stream<Path>`, every operation from Module 2 applies: filter by name, map to size, group by extension, reduce to a total. The walk integrates with the rest of the language instead of being a standalone recursion.
    - **Question:** The legacy `ac3_walkLegacy` method returns an empty list when it hits a permission-denied directory, because `listFiles()` returns `null` and the null guard swallows it. What does `Files.walk` do when it hits the same directory, and why is that behaviour the safer default?

---

## Activity 4: `Files.find` with a `BiPredicate`

*The Hidden Cost* — Activity 3 showed `Files.walk().filter(...)` as the clean replacement for legacy recursion. That pattern is correct for filters that only need the path. The moment your filter needs a file attribute (size, modification time, permissions, whether it is a regular file), the clean pattern hides a performance trap.

Each call to `Files.isRegularFile(path)`, `Files.size(path)`, or `Files.getLastModifiedTime(path)` is a separate `stat` syscall. For a tree of ten thousand files, filtering with `walk().filter(p -> Files.getLastModifiedTime(p).toMillis() > cutoff)` does ten thousand walk-stat syscalls plus another ten thousand stat syscalls for the filter. Twenty thousand round-trips to the kernel, when ten thousand would do.

`Files.find(start, depth, BiPredicate<Path, BasicFileAttributes>)` fixes this. The walker already has a `BasicFileAttributes` object for every path it visits (it needed one to decide whether to descend). Instead of discarding that object and forcing you to fetch it again, `find` passes it into the predicate as the second argument. Same filter logic, half the syscalls. On a fast SSD with a warm cache the difference is invisible. On a cold cache, a network filesystem, or a very large tree, it is the difference between a fast scan and a slow one.

### Steps

1. Locate **TODO 4.1**.

    Create a method called `ac4_findWithWalk` that takes a root `Path` and a size threshold in bytes. Use `Files.walk` and `.filter(p -> Files.size(p) > threshold)`. Count the matches and also count how many `size` lookups were made (increment a counter inside the filter). Return both counts.

2. Paste this snippet:

```java
    private static long[] ac4_findWithWalk(Path root, long thresholdBytes) throws IOException {
        long[] counters = new long[] { 0, 0 };          // [ matches, size_lookups ]
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> {
                      counters[1]++;                    // each filter invocation = one extra stat
                      try {
                          return Files.size(p) > thresholdBytes;
                      } catch (IOException e) {
                          throw new UncheckedIOException(e);
                      }
                  })
                  .forEach(p -> counters[0]++);
        }
        return counters;
    }
```

3. Locate **TODO 4.2**.

    Create a method called `ac4_findWithFind` that takes the same arguments and uses `Files.find(root, Integer.MAX_VALUE, BiPredicate)`. The `BasicFileAttributes` argument supplies the size without a second syscall. Count matches and predicate invocations.

4. Paste this snippet:

```java
    private static long[] ac4_findWithFind(Path root, long thresholdBytes) throws IOException {
        long[] counters = new long[] { 0, 0 };          // [ matches, predicate_invocations ]
        try (Stream<Path> stream = Files.find(root, Integer.MAX_VALUE, (p, attrs) -> {
                counters[1]++;
                return attrs.isRegularFile() && attrs.size() > thresholdBytes;
            })) {
            stream.forEach(p -> counters[0]++);
        }
        return counters;
    }
```

5. Locate **TODO 4.3**.

6. Uncomment the body of `activity4()`.

7. Scroll down to `main()` and uncomment `case "4" -> activity4();`.

8. Compile and run. Select **4**.

9. Observe:
    - Both methods return the same match count. The result is identical, the path to getting there is not.
    - `ac4_findWithWalk` issues one stat per walk entry (for the `isRegularFile` check) *plus* one stat per candidate (for the `Files.size` call). Two syscalls per file in the worst case.
    - `ac4_findWithFind` issues one stat per walk entry, and the attributes object is reused by the predicate. One syscall per file, same answer.
    - On this small tree the wall-clock difference is tens of microseconds, if that. The lesson is not the timing, it is the *shape* of the code: when your filter needs attributes, `find` is the right primitive. When it only needs the path, `walk` is cleaner.
    - **Question:** `Files.walk` returns `Stream<Path>`. `Files.find` also returns `Stream<Path>`, not `Stream<Map.Entry<Path, BasicFileAttributes>>`. The attributes are available inside the predicate but not exposed to downstream operations. What design pressure forced this asymmetry, and what would you do in the downstream pipeline if you needed the attributes again?

---

## Lab Complete

`java.io.File` returns booleans and nulls where NIO.2 throws typed exceptions. `renameTo` is platform-roulette where `Files.move` lets you specify the guarantee you need. Manual recursion becomes `Stream<Path>`, and the moment the filter needs attributes, `walk` becomes `find`. Every activity in this lab is the same refactor applied to a different corner of the filesystem API: trade a short signature for better information, and let the API tell you what your code actually needs.

# Lab 3.3 — WatchService & Filesystem Events

**Module:** 3 — Java NIO
**Files:** `Lab_3_3_WatchService.java`

---

## Activity 1: Register a Watch, See Events

Most change-detection code in the wild is a polling loop: sleep for a second, check `File.lastModified()`, compare to the last known value, repeat. It works, it is easy to write, and it burns CPU to discover nothing has happened ninety-nine times out of a hundred. It also misses fast successive writes, because polling samples at a fixed interval and the filesystem does not.

`WatchService` is the correct primitive. You register a `Path` (specifically a directory) with the service, hand it a set of event kinds you care about (`ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`), and from that point the kernel pushes events to you as they happen. No polling, no missed events, no wasted wakeups. The event loop is simple once you know the shape: call `.take()` or `.poll(timeout)` to block for the next `WatchKey`, drain its events with `.pollEvents()`, and call `.reset()` to put the key back in play.

That last step is the one everyone forgets. Miss the `.reset()` and the key is silently cancelled. No exception, no log line, just no more events from that directory. Activity 1 exercises the full lifecycle with a helper thread that creates, modifies, and deletes a file in the watched directory so you see each event kind fire in order.

### Steps

1. Open `Lab_3_3_WatchService.java`.

2. Read the scaffolding at the top. Note the `WORK_DIR` constant (a directory under `java.io.tmpdir`), the `running` `AtomicBoolean` that signals helper threads to exit, and the `sleep` helper. Each activity creates its own directory under `WORK_DIR` so activities do not interfere with each other.

3. Read the `ac1_helperThread` method already provided in the scaffolding. It waits briefly, then performs three filesystem actions on `target.txt`: create via `Files.writeString`, modify by appending, and delete. Each action is spaced with a short sleep so the events arrive one at a time rather than coalescing.

4. Locate **TODO 1.1**.

    Create a method called `ac1_runWatchLoop` that accepts a `WatchService` and a `Path` (the watched directory). Loop while `running` is true, call `.poll(500, TimeUnit.MILLISECONDS)` to get a `WatchKey`, drain its events with `.pollEvents()`, print each event's kind and context, then call `.reset()`. Break out if `reset()` returns false (the key became invalid).

5. Paste this snippet:

```java
    private static void ac1_runWatchLoop(WatchService watcher, Path watched) throws InterruptedException {
        while (running.get()) {
            WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                Path name = (Path) event.context();
                System.out.println("  event: " + event.kind().name() + "  file: " + name);
            }

            // Critical: reset() re-arms the key. Miss this and no further events arrive.
            boolean valid = key.reset();
            if (!valid) {
                System.out.println("  key no longer valid, exiting loop");
                break;
            }
        }
    }
```

6. Locate **TODO 1.2**.

7. Uncomment the `activity1()` body. It creates the watched directory, opens a `WatchService`, registers the directory for all three event kinds, starts the helper thread, runs the watch loop for about two seconds, then signals shutdown and closes the service.

8. Scroll down to `main()` and uncomment `case "1" -> activity1();`.

9. Compile and run `Lab_3_3_WatchService`.

10. Select **1** from the menu.

11. Observe:
    - Three events print in order: `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`. Each one corresponds to one of the three helper thread actions.
    - The file name in each event is `target.txt`, not the full path. `WatchEvent.context()` returns the path relative to the watched directory, not the absolute path. Resolving it against the watched directory is your job.
    - Between actions the watch loop prints nothing. `poll(500ms)` returns `null` when nothing happened, and the loop just goes around again. No CPU spin, no events missed.
    - The `key.reset()` call is what keeps the events flowing. The comment in the snippet flags this explicitly because it is the most common beginner mistake with this API.
    - **Question:** The helper thread sleeps 300ms between actions. If you reduced that to 10ms, would you still see three distinct events? What determines whether the OS delivers two fast writes as one event or two?

---

## Activity 2: You Watch Directories, Not Files

*The Illusion* — The API is called `WatchService` and it takes a `Path`. A `Path` can point to a file or a directory. So you should be able to watch a single file, right? Hand it `config.yaml`, get notified when `config.yaml` changes, done. Every developer tries this the first time and the API tells them no.

The restriction is a reflection of how the underlying kernel APIs work. Linux uses `inotify`, macOS uses `FSEvents`, Windows uses `ReadDirectoryChangesW`. All three watch directory inodes, not file inodes. When a file changes, what actually fires in the kernel is "something in this directory's contents changed," and the notification carries the affected filename. Java's `WatchService` is a thin portable wrapper over those three, and it inherits their shape: you register directories, you receive events, each event names a child.

If you want to watch a single file, you watch its parent directory and filter events by filename. That is the intended pattern. Activity 2 exercises both halves: first the `NotDirectoryException` you get for trying to register a file directly, then the correct pattern of watching the parent and filtering on the context.

### Steps

1. Locate **TODO 2.1**.

    Create a method called `ac2_tryWatchFile` that accepts a `Path` pointing to a regular file and tries to register it with a `WatchService`. Wrap the registration in a try/catch for `NotDirectoryException` and print the exception's class and message.

2. Paste this snippet:

```java
    private static void ac2_tryWatchFile(Path file) throws IOException {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            file.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            System.out.println("  registration succeeded (unexpected)");
        } catch (NotDirectoryException e) {
            System.out.println("  caught: " + e.getClass().getSimpleName());
            System.out.println("  path:   " + e.getMessage());
        }
    }
```

3. Locate **TODO 2.2**.

    Create a method called `ac2_watchOneFile` that accepts a `Path` to a target file. It should register the file's **parent** directory with a `WatchService`, start a helper thread that modifies both the target file and an unrelated sibling file, and run a watch loop that prints events **only when the event context matches the target filename**.

4. Paste this snippet:

```java
    private static void ac2_watchOneFile(Path target) throws IOException, InterruptedException {
        Path parent     = target.getParent();
        Path targetName = target.getFileName();
        Path sibling    = parent.resolve("noise.txt");

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            parent.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                                     StandardWatchEventKinds.ENTRY_MODIFY);

            Thread helper = new Thread(() -> {
                try {
                    sleep(200);
                    Files.writeString(sibling, "sibling-1");     // should be ignored
                    sleep(200);
                    Files.writeString(target,  "target-1");      // should be reported
                    sleep(200);
                    Files.writeString(sibling, "sibling-2");     // should be ignored
                    sleep(200);
                    Files.writeString(target,  "target-2");      // should be reported
                } catch (IOException ignored) {}
            });
            helper.setDaemon(true);
            helper.start();

            long deadline = System.currentTimeMillis() + 2000;
            while (running.get() && System.currentTimeMillis() < deadline) {
                WatchKey key = watcher.poll(200, TimeUnit.MILLISECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path name = (Path) event.context();
                    if (name.equals(targetName)) {
                        System.out.println("  [match]   " + event.kind().name() + "  " + name);
                    } else {
                        System.out.println("  [ignored] " + event.kind().name() + "  " + name);
                    }
                }
                if (!key.reset()) break;
            }
        }
    }
```

5. Locate **TODO 2.3**.

6. Uncomment the `activity2()` body. It creates the watched directory, writes an initial version of the target file, calls `ac2_tryWatchFile` to see the exception, then calls `ac2_watchOneFile` to see the filtering pattern in action.

7. Scroll down to `main()` and uncomment `case "2" -> activity2();`.

8. Compile and run. Select **2**.

9. Observe:
    - Part A prints `caught: NotDirectoryException` and the path that was rejected. The exception is specific, not a generic `IOException`, which tells you Java knew exactly why this cannot work: you handed it a file and the underlying API only accepts directories.
    - Part B prints four events, two `[match]` lines for `target.txt` and two `[ignored]` lines for `noise.txt`. The watch is registered on the parent, the kernel delivers every event for every file in that directory, and the filter happens in user code.
    - The `[ignored]` lines prove the filtering is yours to do. The OS does not know you only care about one file. It tells you about every change, and you choose which ones matter.
    - In a production single-file watcher, you would typically skip the print for ignored files. The scaffolding shows them so the "you get events for siblings too" point is unambiguous.
    - **Question:** If the directory you want to watch contains ten thousand files and you only care about one, does watching the parent scale? What is the cost of receiving and filtering events for files you do not care about, compared to the kernel filtering them for you?

---

## Activity 3: Platform Semantics, One Save, Many Events

*The Crack* — Your event handler works perfectly in testing. You modify `config.yaml`, the `ENTRY_MODIFY` handler fires once, you reload the config, the test passes. You ship it. In production, the same handler fires two or three times for every save and your config reloader runs three times in a row. No errors, no bad data, just triple the work and confusing log entries.

The reason is that "one save" is not atomic at the filesystem level. Depending on the editor, the OS, and the filesystem, a single logical save can generate multiple `ENTRY_MODIFY` events: one for the file header being updated, one for the body being written, one for metadata like modification time being touched. Some editors do an even more elaborate dance (write to a temp file, fsync, rename over the original) which produces `ENTRY_CREATE` followed by `ENTRY_MODIFY` followed by `ENTRY_DELETE` of the temp. The `WatchService` faithfully reports every one of them, because from the kernel's point of view every one of them is a real event.

The standard fix is debouncing: when you see an event for a given file, record the timestamp and schedule the actual work a short interval later (100 to 200ms is typical). If another event arrives for the same file inside that window, update the timestamp and let the earlier scheduled work be superseded. Only the final event in a burst triggers the handler. The debounce state is a `Map<Path, Instant>` that lives for the duration of the event loop, which callbacks to Lab 2.1 Activity 5: mutation of a `Map` inside a single-threaded event loop is safe precisely because there is only one thread touching it.

### Steps

1. Locate **TODO 3.1**.

    Create a method called `ac3_runNaiveLoop` that accepts a `WatchService` and a deadline in millis. It should poll for events and print every `ENTRY_MODIFY` event with its filename and the current timestamp. No filtering, no debouncing, just print every event the OS delivers.

2. Paste this snippet:

```java
    private static void ac3_runNaiveLoop(WatchService watcher, long deadlineMs) throws InterruptedException {
        System.out.println("  [naive handler] printing every ENTRY_MODIFY event:");
        while (running.get() && System.currentTimeMillis() < deadlineMs) {
            WatchKey key = watcher.poll(100, TimeUnit.MILLISECONDS);
            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    Path name = (Path) event.context();
                    System.out.println("    fired at " + Instant.now() + "  file: " + name);
                }
            }
            if (!key.reset()) break;
        }
    }
```

3. Locate **TODO 3.2**.

    Create a method called `ac3_runDebouncedLoop` that accepts the same parameters plus a debounce window in millis. Maintain a `Map<Path, Instant>` of the last event time per file. When an event arrives, only act on it if the window has elapsed since the last recorded event for that file. Otherwise update the timestamp and skip.

4. Paste this snippet:

```java
    private static void ac3_runDebouncedLoop(WatchService watcher, long deadlineMs, long debounceMs)
            throws InterruptedException {

        System.out.println("  [debounced handler] collapsing events within " + debounceMs + "ms window:");
        Map<Path, Instant> lastFired = new HashMap<>();

        while (running.get() && System.currentTimeMillis() < deadlineMs) {
            WatchKey key = watcher.poll(100, TimeUnit.MILLISECONDS);
            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() != StandardWatchEventKinds.ENTRY_MODIFY) continue;

                Path name = (Path) event.context();
                Instant now = Instant.now();
                Instant last = lastFired.get(name);

                if (last == null || now.toEpochMilli() - last.toEpochMilli() >= debounceMs) {
                    System.out.println("    fired at " + now + "  file: " + name);
                    lastFired.put(name, now);
                } else {
                    // Inside window, suppress. Do not update timestamp, so the window
                    // stays anchored to the first event in the burst.
                }
            }
            if (!key.reset()) break;
        }
    }
```

5. Locate **TODO 3.3**.

6. Uncomment the `activity3()` body. It runs two phases back-to-back on the same watched directory: first the naive handler with a burst-writing helper thread, then the debounced handler with the same burst pattern.

7. Scroll down to `main()` and uncomment `case "3" -> activity3();`.

8. Compile and run. Select **3**.

9. Run Activity 3 two or three times. Event counts in the naive phase are not deterministic, they depend on the OS, the filesystem, and how the JVM's watcher polls the kernel. You should see the naive handler fire more times than the debounced handler fires.

10. Observe:
    - The naive handler may print multiple `fired at` lines per logical save. On Linux inside a container it is often two per save (content write, metadata update). On other platforms and filesystems the count varies. This is the production bug, reproduced.
    - The debounced handler prints at most one `fired at` line per save burst. The `Map<Path, Instant>` holds the last-fired time, and events inside the window are suppressed.
    - The debounce window is a tradeoff. Too short (10ms) and bursts still leak through. Too long (1000ms) and real back-to-back saves look like one. 100 to 200ms is the usual sweet spot for editor-style saves.
    - The `Map` is mutated inside the loop with no synchronisation. This is safe here because the event loop is single-threaded. Callback to Lab 2.1 Activity 5: mutation of captured state is only safe when you can prove nothing else is writing to it concurrently.
    - **Question:** The debounced handler fires on the **first** event in a burst and suppresses the rest. An alternative design fires on the **last** event, by scheduling work for `now + window` and cancelling the scheduled work if another event arrives inside the window. When would "fire on last" be the correct choice, and what extra machinery does it need compared to "fire on first"?

---

## Lab Complete

WatchService replaces polling with kernel-pushed events, but only for directories, and only with the discipline to `reset()` the key and to debounce bursts that the filesystem treats as multiple events. One more case worth knowing about lives outside these activities: when events arrive faster than your loop drains them, the service delivers a special `OVERFLOW` event meaning "I dropped events, I will not tell you which ones," and the correct response is a full re-scan of the watched state, not event replay. And `WatchService` is not recursive: to watch a tree, walk it with `Files.walk` and register each subdirectory individually, adding new registrations as `ENTRY_CREATE` events arrive for directories.

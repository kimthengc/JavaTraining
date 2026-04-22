import java.io.IOException;

/**
 * Support scaffolding for Lab 2.3, Activity 3.
 *
 * Simulates any real-world API that throws a checked exception:
 *   - File I/O (Files.readString throws IOException)
 *   - Network calls (HttpClient.send throws IOException, InterruptedException)
 *   - Reflection (Class.forName throws ClassNotFoundException)
 *   - Custom parsers you've written yourself
 *
 * The behaviour is deterministic: the input "BAD" triggers an IOException,
 * everything else resolves to "value-for-<id>". This predictability lets the
 * activity focus on the checked-exception mechanics without fighting flaky I/O.
 */
public class CheckedService {

    public String lookup(String id) throws IOException {
        if ("BAD".equals(id)) {
            throw new IOException("simulated I/O failure for id=" + id);
        }
        return "value-for-" + id;
    }
}

/**
 * A functional interface that permits checked exceptions.
 *
 * This is the entire reason the unchecked() helper in Activity 3 can exist.
 * java.util.function.Function<T, R> declares apply(T) with no throws clause,
 * which is why calling a checked-throwing method inside .map() fails to
 * compile. CheckedFunction<T, R> mirrors Function but declares throws
 * Exception on its single abstract method — so a lambda body that throws
 * a checked exception now has a functional interface it can satisfy.
 *
 * Note the @FunctionalInterface annotation. It is not required for the type
 * to work as a lambda target (the compiler infers SAM interfaces automatically),
 * but it is strongly recommended: it tells the compiler to enforce the
 * single-abstract-method rule, and it documents intent to anyone reading the
 * file that this type exists specifically to be implemented as a lambda.
 */
@FunctionalInterface
interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;
}

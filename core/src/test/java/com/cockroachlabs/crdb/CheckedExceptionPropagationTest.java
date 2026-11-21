package com.cockroachlabs.crdb;

import com.cockroachlabs.crdb.annotations.Region;
import com.cockroachlabs.crdb.context.CrdbContext;
import com.cockroachlabs.crdb.context.CrdbContextValues;
import com.cockroachlabs.crdb.retry.RetryEngine;
import com.cockroachlabs.crdb.retry.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A checked exception thrown by the user's work must reach the caller as the
 * SAME INSTANCE — never wrapped in a generic {@link RuntimeException} — through
 * both annotated-method paths:
 * <ul>
 *   <li>the context path ({@link CrdbContext#with}) used by
 *       {@code @FollowerRead}/{@code @VirtualCluster}/{@code @Region}/{@code @Workload};</li>
 *   <li>the retry path ({@link RetryEngine#run}) used by
 *       {@code @CrdbRetry}/{@code @Transactional(retry=true)}.</li>
 * </ul>
 * Unchecked exceptions still pass through unchanged, and success still returns.
 */
class CheckedExceptionPropagationTest {

    private static final RetryPolicy POLICY = new RetryPolicy(3, 1, 5, 2.0, 0.0);

    // No-op sleeper so the retry path does not actually wait.
    private static RetryEngine engine() {
        return new RetryEngine(() -> 0.5, millis -> { });
    }

    // ---- context path: CrdbContext.with ----------------------------------

    @Test
    void contextPathPropagatesCheckedExceptionUnwrapped() {
        IOException checked = new IOException("boom");

        Exception thrown = assertThrows(Exception.class, () ->
                CrdbContext.with(CrdbContextValues.empty(), () -> {
                    throw checked;
                }));

        assertSame(checked, thrown, "checked exception must reach caller unwrapped");
    }

    @Test
    void contextPathPassesUncheckedThrough() {
        IllegalStateException unchecked = new IllegalStateException("x");

        Exception thrown = assertThrows(IllegalStateException.class, () ->
                CrdbContext.with(CrdbContextValues.empty(), () -> {
                    throw unchecked;
                }));

        assertSame(unchecked, thrown);
    }

    @Test
    void contextPathReturnsResultOnSuccess() {
        assertEquals("ok", CrdbContext.with(CrdbContextValues.empty(), () -> "ok"));
    }

    // ---- retry path: RetryEngine.run -------------------------------------

    @Test
    void retryPathPropagatesCheckedExceptionUnwrapped() {
        IOException checked = new IOException("io");

        Exception thrown = assertThrows(Exception.class, () ->
                engine().run(POLICY, () -> {
                    throw checked;
                }));

        assertSame(checked, thrown, "checked exception must reach caller unwrapped");
    }

    @Test
    void retryPathPassesUncheckedThrough() {
        IllegalStateException unchecked = new IllegalStateException("y");

        Exception thrown = assertThrows(IllegalStateException.class, () ->
                engine().run(POLICY, () -> {
                    throw unchecked;
                }));

        assertSame(unchecked, thrown);
    }

    @Test
    void retryPathReturnsResultOnSuccess() {
        assertEquals("ok", engine().run(POLICY, () -> "ok"));
    }

    // ---- end-to-end: interface (JDK Proxy) annotated method --------------

    interface Reader {
        String read() throws IOException;
    }

    static final class ReaderImpl implements Reader {
        final IOException boom = new IOException("end-to-end");

        @Override
        @Region("us-east1")
        public String read() throws IOException {
            throw boom;
        }
    }

    @Test
    void annotatedInterfaceMethodPropagatesCheckedExceptionUnwrapped() {
        ReaderImpl impl = new ReaderImpl();
        Reader enhanced = Crdb.enhance((Reader) impl);

        IOException thrown = assertThrows(IOException.class, enhanced::read);

        assertSame(impl.boom, thrown, "checked exception must survive the proxy edge unwrapped");
    }
}

package com.cockroachlabs.crdb.proxy;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.FollowerRead;
import com.cockroachlabs.crdb.context.CrdbContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Follower-read staleness composition and fail-fast validation. No database.
 *
 * <p>Covers both the pure {@link CrdbDispatcher#followerReadStaleness} resolver
 * and its wiring through the proxy into {@link CrdbContext}.
 */
class FollowerReadStalenessTest {

    // ---- annotated specimens; annotations can't be constructed directly ----

    @FollowerRead
    void marker() {
    }

    @FollowerRead(hoursAgo = 1, minutesAgo = 30)
    void oneHourThirty() {
    }

    @FollowerRead(secondsAgo = 90)
    void ninetySeconds() {
    }

    @FollowerRead(hoursAgo = 1, minutesAgo = 1, secondsAgo = 1)
    void allThreeParts() {
    }

    @FollowerRead(staleness = "'-1m'")
    void rawExpression() {
    }

    @FollowerRead(staleness = "'-1m'", secondsAgo = 5)
    void partsBeatRawStaleness() {
    }

    @FollowerRead(minutesAgo = -1)
    void negativePart() {
    }

    private static FollowerRead annotationOn(String method) throws NoSuchMethodException {
        Method m = FollowerReadStalenessTest.class.getDeclaredMethod(method);
        return m.getAnnotation(FollowerRead.class);
    }

    // ---- resolver: composition + precedence --------------------------------

    @Test
    void defaultMarkerUsesFollowerReadTimestamp() throws Exception {
        assertEquals("follower_read_timestamp()",
                CrdbDispatcher.followerReadStaleness(annotationOn("marker")));
    }

    @Test
    void partsAddUp() throws Exception {
        assertEquals("'-5400s'",
                CrdbDispatcher.followerReadStaleness(annotationOn("oneHourThirty")));
    }

    @Test
    void singlePartComposes() throws Exception {
        assertEquals("'-90s'",
                CrdbDispatcher.followerReadStaleness(annotationOn("ninetySeconds")));
    }

    @Test
    void allThreePartsAddUp() throws Exception {
        assertEquals("'-3661s'",
                CrdbDispatcher.followerReadStaleness(annotationOn("allThreeParts")));
    }

    @Test
    void rawStalenessUsedWhenNoPartsSet() throws Exception {
        assertEquals("'-1m'",
                CrdbDispatcher.followerReadStaleness(annotationOn("rawExpression")));
    }

    @Test
    void partsTakePrecedenceOverRawStaleness() throws Exception {
        assertEquals("'-5s'",
                CrdbDispatcher.followerReadStaleness(annotationOn("partsBeatRawStaleness")));
    }

    // ---- resolver: fail fast ----------------------------------------------

    @Test
    void negativePartThrows() throws Exception {
        FollowerRead ann = annotationOn("negativePart");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CrdbDispatcher.followerReadStaleness(ann));
        // Message names the offending annotation so the failure is actionable.
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("@FollowerRead"));
    }

    // ---- end-to-end through the proxy into CrdbContext ---------------------

    interface Reader {
        String staleness();

        String composed();

        String badWindow();
    }

    static final class ReaderImpl implements Reader {
        @Override
        @FollowerRead
        public String staleness() {
            return CrdbContext.followerReadStaleness().orElse("none");
        }

        @Override
        @FollowerRead(hoursAgo = 1, minutesAgo = 30)
        public String composed() {
            return CrdbContext.followerReadStaleness().orElse("none");
        }

        @Override
        @FollowerRead(secondsAgo = -5)
        public String badWindow() {
            return CrdbContext.followerReadStaleness().orElse("none");
        }
    }

    @Test
    void proxyBindsDefaultFollowerRead() {
        Reader r = Crdb.enhance((Reader) new ReaderImpl());
        assertEquals("follower_read_timestamp()", r.staleness());
    }

    @Test
    void proxyBindsComposedFollowerRead() {
        Reader r = Crdb.enhance((Reader) new ReaderImpl());
        assertEquals("'-5400s'", r.composed());
    }

    @Test
    void proxyFailsFastOnNegativeWindow() {
        Reader r = Crdb.enhance((Reader) new ReaderImpl());
        assertThrows(IllegalStateException.class, r::badWindow);
    }
}

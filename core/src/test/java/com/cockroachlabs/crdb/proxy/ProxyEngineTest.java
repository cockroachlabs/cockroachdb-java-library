package com.cockroachlabs.crdb.proxy;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.CrdbRetry;
import com.cockroachlabs.crdb.annotations.Region;
import com.cockroachlabs.crdb.context.CrdbContext;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** Proxy composition tests that need no database. */
class ProxyEngineTest {

    // ---- interface strategy (JDK Proxy) ----------------------------------

    interface Worker {
        String doWork() throws SQLException;

        String whereAmI();
    }

    static final class WorkerImpl implements Worker {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        @CrdbRetry(maxRetries = 5, baseDelayMillis = 1, maxDelayMillis = 5)
        public String doWork() throws SQLException {
            if (calls.incrementAndGet() <= 2) {
                throw new SQLException("restart", "40001");
            }
            return "done";
        }

        @Override
        @Region("us-east1")
        public String whereAmI() {
            return CrdbContext.region().orElse("none");
        }
    }

    @Test
    void interfaceProxyRetriesUntilSuccess() throws SQLException {
        WorkerImpl impl = new WorkerImpl();
        Worker enhanced = Crdb.enhance((Worker) impl);
        assertNotSame(impl, enhanced);
        assertEquals("done", enhanced.doWork());
        assertEquals(3, impl.calls.get());
    }

    @Test
    void interfaceProxyBindsRegionContext() {
        Worker enhanced = Crdb.enhance((Worker) new WorkerImpl());
        assertEquals("us-east1", enhanced.whereAmI());
    }

    // ---- class strategy (ByteBuddy subclass) -----------------------------

    static class Pojo {
        public int base;
        public final AtomicInteger calls = new AtomicInteger();

        public Pojo() {
        }

        @CrdbRetry(maxRetries = 5, baseDelayMillis = 1, maxDelayMillis = 5)
        public int compute() throws SQLException {
            if (calls.incrementAndGet() <= 1) {
                throw new SQLException("restart", "40001");
            }
            return base + 1;
        }

        @Region("eu-west1")
        public String region() {
            return CrdbContext.region().orElse("none");
        }
    }

    @Test
    void classProxyRetriesAndPreservesState() throws SQLException {
        Pojo target = new Pojo();
        target.base = 41;
        Pojo enhanced = Crdb.enhance(target);
        assertNotSame(target, enhanced);
        assertEquals(42, enhanced.compute(), "state (base=41) copied into the subclass");
        assertEquals(2, enhanced.calls.get());
    }

    @Test
    void classProxyBindsRegionContext() {
        Pojo enhanced = Crdb.enhance(new Pojo());
        assertEquals("eu-west1", enhanced.region());
    }
}

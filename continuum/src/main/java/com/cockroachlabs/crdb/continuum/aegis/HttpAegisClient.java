package com.cockroachlabs.crdb.continuum.aegis;

import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AegisConfig;
import com.cockroachlabs.crdb.spi.AppEvent;
import com.cockroachlabs.crdb.spi.Finding;
import com.cockroachlabs.crdb.spi.Recommendation;

import java.util.List;

/**
 * HTTP-backed Cockroach Aegis client.
 *
 * <p>The Aegis public API is not yet available; this class defines the shape of
 * the real integration but its operations currently throw. The interface and the
 * {@link SimulatedAegisClient simulated} path are complete and tested.
 *
 * <p>// TODO: wire the real Aegis API (findings/recommendations/emit) when it is
 * public. Do not invent a wire protocol before then.
 */
public final class HttpAegisClient implements AegisClient {

    private final AegisConfig config;

    public HttpAegisClient(AegisConfig config) {
        this.config = config;
    }

    /** The configuration this client would use once the Aegis API is public. */
    public AegisConfig config() {
        return config;
    }

    @Override
    public List<Finding> findings() {
        throw new UnsupportedOperationException("Aegis API not yet public");
    }

    @Override
    public List<Recommendation> recommendations() {
        throw new UnsupportedOperationException("Aegis API not yet public");
    }

    @Override
    public void emit(AppEvent event) {
        throw new UnsupportedOperationException("Aegis API not yet public");
    }
}

package cz.insuit.ares.spring;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ares.*}. Each one unset keeps the client's own default.
 *
 * @param baseUri the API, up to {@code /rest}; production unless set, such as to a test's mock server
 * @param requestTimeout how long one attempt may take
 * @param retries how many times a 5xx or no answer is tried again
 * @param retryDelay the wait before the first retry, doubled before each next one
 */
@ConfigurationProperties("ares")
public record AresProperties(
        @Nullable URI baseUri,
        @Nullable Duration requestTimeout,
        @Nullable Integer retries,
        @Nullable Duration retryDelay) {}

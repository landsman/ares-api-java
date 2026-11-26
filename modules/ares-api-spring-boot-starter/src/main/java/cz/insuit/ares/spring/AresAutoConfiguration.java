package cz.insuit.ares.spring;

import cz.insuit.ares.AresClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * One {@link AresClient} for the application, built from {@code ares.*}, unless the application defines its own.
 * Spring closes it on shutdown, which releases the HTTP client it made.
 */
@AutoConfiguration
@EnableConfigurationProperties(AresProperties.class)
public class AresAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AresClient aresClient(AresProperties properties) {
        // Only what the application set is passed on, so each default lives in one place: the client.
        var builder = AresClient.builder();
        var baseUri = properties.baseUri();
        if (baseUri != null) {
            builder.baseUri(baseUri);
        }
        var requestTimeout = properties.requestTimeout();
        if (requestTimeout != null) {
            builder.requestTimeout(requestTimeout);
        }
        var retries = properties.retries();
        if (retries != null) {
            builder.retries(retries);
        }
        var retryDelay = properties.retryDelay();
        if (retryDelay != null) {
            builder.retryDelay(retryDelay);
        }
        return builder.build();
    }
}

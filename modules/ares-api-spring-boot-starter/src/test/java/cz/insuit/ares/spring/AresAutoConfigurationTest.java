package cz.insuit.ares.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import cz.insuit.ares.AresClient;
import cz.insuit.ares.AresNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("ARES auto-configuration")
class AresAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AresAutoConfiguration.class));

    @Test
    @DisplayName("with nothing set: a client for production")
    void defaults() {
        runner.run(context -> assertThat(context).hasSingleBean(AresClient.class));
    }

    @Test
    @DisplayName("ares.* binds")
    void properties() {
        runner.withPropertyValues(
                        "ares.base-uri=http://localhost:8089/ekonomicke-subjekty-v-be/rest",
                        "ares.request-timeout=3s",
                        "ares.retries=1",
                        "ares.retry-delay=100ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AresProperties.class);
                    assertThat(properties.baseUri()).hasToString("http://localhost:8089/ekonomicke-subjekty-v-be/rest");
                    assertThat(properties.requestTimeout()).hasSeconds(3);
                    assertThat(properties.retries()).isEqualTo(1);
                    assertThat(properties.retryDelay()).hasMillis(100);
                });
    }

    @Test
    @DisplayName("ares.base-uri and ares.retries reach the client: one request, to that server")
    void reachesClient() throws IOException, URISyntaxException {
        List<String> requested = new CopyOnWriteArrayList<>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var address = server.getAddress();
            var base = new URI("http", null, address.getHostString(), address.getPort(), "/rest", null, null);
            runner.withPropertyValues("ares.base-uri=" + base, "ares.retries=0").run(context -> {
                var client = context.getBean(AresClient.class);
                assertThatThrownBy(() -> client.company("27074358")).isNotInstanceOf(AresNotFoundException.class);
            });
        } finally {
            server.stop(0);
        }
        assertThat(requested).containsExactly("/rest/ekonomicke-subjekty/27074358");
    }

    @Test
    @DisplayName("the application's own client replaces the starter's")
    void ownClient() {
        runner.withUserConfiguration(OwnClient.class)
                .run(context -> assertThat(context.getBean(AresClient.class)).isSameAs(OwnClient.CLIENT));
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnClient {
        static final AresClient CLIENT = new AresClient();

        @Bean
        AresClient aresClient() {
            return CLIENT;
        }
    }
}

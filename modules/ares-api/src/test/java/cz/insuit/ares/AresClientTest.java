package cz.insuit.ares;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Against the JDK's own HTTP server on a random port, answering each path from a queue the test fills. */
@DisplayName("ARES client")
class AresClientTest {

    private static final String REST = "/ekonomicke-subjekty-v-be/rest/";

    /** The answers per path, in order; the last one repeats. A path without any is a 404. */
    private final Map<String, Deque<Answer>> answers = new ConcurrentHashMap<>();

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private URI base;
    private AresClient client;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath().substring(REST.length());
            requested.add(path);
            var queue = answers.get(path);
            var answer = queue == null ? new Answer(404, "") : queue.size() > 1 ? queue.poll() : queue.peek();
            if (answer.delay() > 0) {
                try {
                    Thread.sleep(answer.delay());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
            // A body of zero length is sent as -1, or the exchange hangs.
            exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (var out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        var address = server.getAddress();
        // The seven-argument constructor brackets an IPv6 loopback.
        base = new URI("http", null, address.getHostString(), address.getPort(), REST, null, null);
        client = AresClient.builder()
                .baseUri(base)
                .retryDelay(Duration.ofMillis(1))
                .build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    @DisplayName("a company: every field read, no contacts, and no RŽP call for a person it does not have")
    void company() {
        answer("ekonomicke-subjekty/27074358", 200, fixture("company.json"));

        var company = client.company("27074358");

        assertEquals("27074358", company.ico());
        assertEquals("CZ27074358", company.dic());
        assertEquals("Asseco Central Europe, a.s.", company.name());
        assertEquals("121", company.legalForm());
        assertEquals(LocalDate.of(2003, 8, 6), company.established());
        assertEquals(
                new AresAddress(
                        "CZ",
                        "Praha",
                        "Budějovická",
                        778,
                        3,
                        "a",
                        14000,
                        "Praha 4",
                        "Budějovická 778/3a, Michle, 14000 Praha 4"),
                company.address());
        assertNull(company.contacts());
        assertNull(company.soleTrader());
        assertEquals(List.of("ekonomicke-subjekty/27074358"), requested);
    }

    @Test
    @DisplayName("only the IČO and the name: the rest is null, the address too, part by part")
    void minimal() {
        answer("ekonomicke-subjekty/12345678", 200, "{\"ico\":\"12345678\",\"obchodniJmeno\":\"Firma\"}");

        var company = client.company("12345678");

        assertEquals("Firma", company.name());
        assertNull(company.dic());
        assertNull(company.established());
        assertEquals(new AresAddress(null, null, null, null, null, null, null, null, null), company.address());
    }

    @Test
    @DisplayName("NRPZS wins over RŠ when both are active, and its primary record over the first")
    void healthProvider() {
        answer("ekonomicke-subjekty/00023001", 200, fixture("hospital.json"));
        answer("ekonomicke-subjekty-nrpzs/00023001", 200, fixture("nrpzs.json"));

        var company = client.company("00023001");

        assertEquals(
                new AresContacts("info@nemocnice.example", "+420123456789", null, "nemocnice.example"),
                company.contacts());
        assertEquals(List.of("ekonomicke-subjekty/00023001", "ekonomicke-subjekty-nrpzs/00023001"), requested);
    }

    @Test
    @DisplayName("a school's contacts from RŠ, the first of its e-mails")
    void school() {
        answer("ekonomicke-subjekty/00027383", 200, fixture("school.json"));
        answer("ekonomicke-subjekty-rs/00027383", 200, fixture("rs.json"));

        var contacts = client.company("00027383").contacts();

        assertEquals(new AresContacts("skola@skola.example", "+420111222333", null, null), contacts);
    }

    @Test
    @DisplayName("a contacts register that fails leaves the subject found and its contacts null")
    void contactsFail() {
        answer("ekonomicke-subjekty/00023001", 200, fixture("hospital.json"));
        answer("ekonomicke-subjekty-nrpzs/00023001", 500, "");

        var company = client.company("00023001");

        assertEquals("Nemocnice Na Kraji", company.name());
        assertNull(company.contacts());
    }

    @Test
    @DisplayName("a sole trader's person from RŽP")
    void soleTrader() {
        answer("ekonomicke-subjekty/12345678", 200, fixture("sole-trader.json"));
        answer("ekonomicke-subjekty-rzp/12345678", 200, fixture("rzp.json"));

        var company = client.company("12345678");

        assertEquals(new AresSoleTrader("Jan", "Novák", LocalDate.of(1994, 3, 17)), company.soleTrader());
        assertEquals(1001, company.address().postalCode());
    }

    @Test
    @DisplayName("an RŽP that answers something unreadable leaves the person unknown")
    void soleTraderUnreadable() {
        answer("ekonomicke-subjekty/12345678", 200, fixture("sole-trader.json"));
        answer("ekonomicke-subjekty-rzp/12345678", 200, "<html>");

        assertNull(client.company("12345678").soleTrader());
    }

    @Test
    @DisplayName("404 is not found, and not retried")
    void notFound() {
        var e = assertThrows(AresNotFoundException.class, () -> client.company("99999999"));

        assertEquals("99999999", e.ico());
        assertEquals(1, requested.size());
    }

    @Test
    @DisplayName("any other 4xx is unavailable, and not retried")
    void otherStatus() {
        answer("ekonomicke-subjekty/27074358", 400, "{\"kod\":\"CHYBA_VSTUPU\"}");

        var e = assertThrows(AresUnavailableException.class, () -> client.company("27074358"));

        assertTrue(Objects.requireNonNull(e.getMessage()).contains("400"));
        assertEquals(1, requested.size());
    }

    @Test
    @DisplayName("a 5xx is retried, and the answer after it counts")
    void retriesServerError() {
        answer("ekonomicke-subjekty/27074358", 503, "");
        answer("ekonomicke-subjekty/27074358", 200, fixture("company.json"));

        assertEquals("Asseco Central Europe, a.s.", client.company("27074358").name());
        assertEquals(2, requested.size());
    }

    @Test
    @DisplayName("a 5xx that lasts is unavailable after three attempts")
    void serverErrorLasts() {
        answer("ekonomicke-subjekty/27074358", 502, "");

        assertThrows(AresUnavailableException.class, () -> client.company("27074358"));
        assertEquals(3, requested.size());
    }

    @Test
    @DisplayName("a body that is not JSON, or JSON without a name, is unavailable")
    void unreadable() {
        answer("ekonomicke-subjekty/27074358", 200, "not json");
        assertThrows(AresUnavailableException.class, () -> client.company("27074358"));

        answers.clear();
        answer("ekonomicke-subjekty/27074358", 200, "{\"ico\":\"27074358\"}");
        assertThrows(AresUnavailableException.class, () -> client.company("27074358"));
    }

    @Test
    @DisplayName("a refused connection is unavailable once the retries are spent")
    void refused() throws IOException {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        try (var closed = AresClient.builder()
                .baseUri(URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port + REST))
                .retries(1)
                .retryDelay(Duration.ofMillis(1))
                .build()) {
            var e = assertThrows(AresUnavailableException.class, () -> closed.company("27074358"));
            assertInstanceOf(IOException.class, e.getCause());
        }
    }

    @Test
    @DisplayName("a register that accepts and never answers times out instead of holding the caller")
    void timeout() {
        answers.put("ekonomicke-subjekty/27074358", new ArrayDeque<>(List.of(new Answer(200, "{}", 5_000))));
        try (var impatient = AresClient.builder()
                .baseUri(base)
                .requestTimeout(Duration.ofMillis(200))
                .retries(0)
                .build()) {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(3),
                    () -> assertThrows(AresUnavailableException.class, () -> impatient.company("27074358")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2707435", "270743581", "../27074358", "2707435a", ""})
    @DisplayName("anything but eight digits is refused before a request goes out")
    void invalidIco(String ico) {
        assertThrows(IllegalArgumentException.class, () -> client.company(ico));
        assertTrue(requested.isEmpty());
    }

    private void answer(String path, int status, String body) {
        answers.computeIfAbsent(path, p -> new ArrayDeque<>()).add(new Answer(status, body));
    }

    private static String fixture(String name) {
        try (var in = AresClientTest.class.getResourceAsStream("/ares/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Answer(int status, String body, long delay) {
        Answer(int status, String body) {
            this(status, body, 0);
        }
    }
}

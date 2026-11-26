package cz.insuit.ares;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Looks a subject up in ARES's public REST API, as described in its
 * <a href="https://ares.gov.cz/swagger-ui/">Swagger UI</a>. No key, no sign-up.
 *
 * <p>One lookup is one request, and up to two more when the register says there is more to read: the contacts of a
 * health provider (NRPZS) or a school (RŠ), and the person behind a sole trader (RŽP). Those extras are best effort:
 * when one fails, its field is null and the lookup still succeeds.
 *
 * <p>A 5xx or no answer is retried, waiting {@link Builder#retryDelay} and twice that before each next try. The worst
 * wait is two such chains, the subject and then its extra, each {@code (retries + 1) × requestTimeout} plus the
 * waits; a caller with a person waiting on the answer should pass a short timeout and fewer retries.
 *
 * <p>Thread-safe. {@link #close()} releases the HTTP client it made itself, not one passed to {@link
 * Builder#httpClient}.
 */
public final class AresClient implements AutoCloseable {

    public static final URI PRODUCTION = URI.create("https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/");

    private static final System.Logger LOG = System.getLogger(AresClient.class.getName());
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern ICO = Pattern.compile("\\d{8}");
    private static final String ACTIVE = "AKTIVNI";
    private static final int ERROR_BODY_LIMIT = 200;

    private final HttpClient http;
    private final boolean ownsHttp;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final int retries;
    private final Duration retryDelay;

    /** A client for production, with the defaults of {@link Builder}. */
    public AresClient() {
        this(builder());
    }

    private AresClient(Builder builder) {
        this.ownsHttp = builder.http == null;
        // Without a connect timeout, a register that accepts the connection and never answers holds the caller for
        // ever: HttpClient waits without limit by default.
        this.http = builder.http != null
                ? builder.http
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var base = builder.baseUri.toString();
        this.baseUri = URI.create(base.endsWith("/") ? base : base + "/");
        this.requestTimeout = builder.requestTimeout;
        this.retries = builder.retries;
        this.retryDelay = builder.retryDelay;
    }

    /** Starts a client for production; set only what differs. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() {
        if (ownsHttp) {
            http.close();
        }
    }

    /**
     * The subject with this IČO, with its contacts and its person when the register has them.
     *
     * @param ico eight digits, as the register writes it: {@code 00023001}, not {@code 23001}
     * @throws IllegalArgumentException when {@code ico} is not eight digits; no request is made
     * @throws AresNotFoundException when no subject has it
     * @throws AresUnavailableException when the register did not give a usable answer, after the retries
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public AresCompany company(String ico) {
        if (!ICO.matcher(ico).matches()) {
            // Checked before it goes into the URL path, where anything else could reach another endpoint.
            throw new IllegalArgumentException("an IČO is eight digits");
        }
        var root = fetch(ico, "ekonomicke-subjekty/" + ico);
        var name = text(root.path("obchodniJmeno"));
        var answeredIco = text(root.path("ico"));
        if (name == null || answeredIco == null) {
            throw new AresUnavailableException(ico, "ARES answered " + ico + " without a name or an IČO", null);
        }
        var legalForm = text(root.path("pravniForma"));
        var registrations = root.path("seznamRegistraci");
        AresContacts contacts = null;
        if (active(registrations, "stavZdrojeNrpzs")) {
            contacts = optional(ico, "ekonomicke-subjekty-nrpzs/" + ico, AresClient::contacts);
        } else if (active(registrations, "stavZdrojeRs")) {
            contacts = optional(ico, "ekonomicke-subjekty-rs/" + ico, AresClient::contacts);
        }
        // A company's RŽP record names no person, so the call would cost the wait for nothing.
        AresSoleTrader soleTrader = null;
        if (active(registrations, "stavZdrojeRzp")
                && AresLegalFormGroup.of(legalForm) == AresLegalFormGroup.SOLE_TRADER) {
            soleTrader = optional(ico, "ekonomicke-subjekty-rzp/" + ico, AresClient::soleTrader);
        }
        return new AresCompany(
                answeredIco,
                text(root.path("dic")),
                name,
                legalForm,
                date(root.path("datumVzniku")),
                address(root.path("sidlo")),
                contacts,
                soleTrader);
    }

    /** An extra read that may fail without failing the lookup; an interrupt still stops it. */
    private <T> @Nullable T optional(String ico, String path, Function<JsonNode, @Nullable T> read) {
        try {
            return read.apply(fetch(ico, path));
        } catch (AresException e) {
            LOG.log(Level.WARNING, () -> "ARES " + path + " failed, left out of the lookup", e);
            return null;
        }
    }

    private JsonNode fetch(String ico, String path) {
        var request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        var delay = retryDelay;
        HttpResponse<String> response;
        for (int attempt = 0; ; attempt++) {
            if (attempt > 0) {
                sleep(delay, path);
                delay = delay.multipliedBy(2);
            }
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException | UncheckedIOException e) {
                if (attempt >= retries) {
                    throw new AresUnavailableException(ico, "ARES " + path + " did not answer", e);
                }
                continue;
            } catch (InterruptedException e) {
                throw cancelled(path, e);
            }
            if (response.statusCode() / 100 != 5 || attempt >= retries) {
                break;
            }
        }
        int status = response.statusCode();
        if (status == 404) {
            throw new AresNotFoundException(ico);
        }
        var body = response.body();
        if (status / 100 != 2) {
            var excerpt = body.length() <= ERROR_BODY_LIMIT ? body : body.substring(0, ERROR_BODY_LIMIT);
            throw new AresUnavailableException(ico, "ARES " + path + " answered " + status + ": " + excerpt, null);
        }
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            throw new AresUnavailableException(ico, "ARES " + path + " answered with a body that is not JSON", e);
        }
    }

    private static void sleep(Duration delay, String path) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            throw cancelled(path, e);
        }
    }

    /** Whoever owns the thread asked it to stop; that is not ARES failing, so not an exception a retry catches. */
    private static CancellationException cancelled(String path, InterruptedException e) {
        Thread.currentThread().interrupt();
        var cancelled = new CancellationException("ARES " + path + " was interrupted");
        cancelled.initCause(e);
        return cancelled;
    }

    private static boolean active(JsonNode registrations, String source) {
        return ACTIVE.equals(text(registrations.path(source)));
    }

    private static AresAddress address(JsonNode seat) {
        return new AresAddress(
                text(seat.path("kodStatu")),
                text(seat.path("nazevObce")),
                text(seat.path("nazevUlice")),
                integer(seat.path("cisloDomovni")),
                integer(seat.path("cisloOrientacni")),
                text(seat.path("cisloOrientacniPismeno")),
                integer(seat.path("psc")),
                text(seat.path("nazevMestskeCastiObvodu")),
                text(seat.path("textovaAdresa")));
    }

    /** NRPZS and RŠ share the shape, except that RŠ lists e-mails where NRPZS has one. */
    private static @Nullable AresContacts contacts(JsonNode root) {
        var record = primary(root);
        var contacts = record == null ? null : present(record.path("kontakty"));
        if (contacts == null) {
            return null;
        }
        var email = contacts.path("email");
        return new AresContacts(
                text(email.isArray() ? email.path(0) : email),
                text(contacts.path("telefon")),
                text(contacts.path("fax")),
                text(contacts.path("www")));
    }

    private static @Nullable AresSoleTrader soleTrader(JsonNode root) {
        var record = primary(root);
        var person = record == null ? null : present(record.path("osobaPodnikatel"));
        if (person == null) {
            return null;
        }
        return new AresSoleTrader(
                text(person.path("jmeno")), text(person.path("prijmeni")), date(person.path("datumNarozeni")));
    }

    /** The record marked primary, or the first when none is. */
    private static @Nullable JsonNode primary(JsonNode root) {
        var records = root.path("zaznamy");
        if (!records.isArray() || records.isEmpty()) {
            return null;
        }
        for (var record : records) {
            var primary = record.path("primarniZaznam");
            if (primary.isBoolean() && primary.booleanValue()) {
                return record;
            }
        }
        return records.get(0);
    }

    private static @Nullable JsonNode present(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node;
    }

    private static @Nullable String text(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        var value = node.stringValue().strip();
        return value.isEmpty() ? null : value;
    }

    private static @Nullable Integer integer(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : null;
    }

    /** Null for a date the register wrote in a shape it does not document, rather than failing the lookup. */
    private static @Nullable LocalDate date(JsonNode node) {
        var text = text(node);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** What differs from production. */
    public static final class Builder {
        private URI baseUri = PRODUCTION;
        private Duration requestTimeout = Duration.ofSeconds(10);
        private int retries = 2;
        private Duration retryDelay = Duration.ofMillis(500);
        private @Nullable HttpClient http;

        private Builder() {}

        /** Where the API lives, the path up to {@code /rest}; {@link #PRODUCTION} unless a test has its own server. */
        public Builder baseUri(URI baseUri) {
            this.baseUri = requireNonNull(baseUri, "baseUri");
            return this;
        }

        /** How long one attempt may take, connecting included; 10 seconds unless set. */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requireNonNull(requestTimeout, "requestTimeout");
            return this;
        }

        /** How many times a 5xx or no answer is tried again; 2 unless set, so three attempts. */
        public Builder retries(int retries) {
            if (retries < 0) {
                throw new IllegalArgumentException("retries " + retries + " is negative");
            }
            this.retries = retries;
            return this;
        }

        /** The wait before the first retry, doubled before each next one; 500 ms unless set. */
        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = requireNonNull(retryDelay, "retryDelay");
            return this;
        }

        /** An HTTP client of the caller's, which {@link AresClient#close()} leaves open. */
        public Builder httpClient(HttpClient http) {
            this.http = requireNonNull(http, "http");
            return this;
        }

        public AresClient build() {
            return new AresClient(this);
        }
    }
}

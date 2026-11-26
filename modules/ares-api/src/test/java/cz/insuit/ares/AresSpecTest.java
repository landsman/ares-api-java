package cz.insuit.ares;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Holds the client and its fixtures to {@code spec/ares-openapi.json}, the register's own OpenAPI document. When ARES
 * renames, retypes or drops a field and {@code make openapi} brings that in, a test here fails, naming the field,
 * instead of the client quietly reading null against fixtures that still have it.
 *
 * <p>It checks names and types only, not {@code required}, enums or patterns: what decides whether the client can
 * read an answer at all.
 */
@DisplayName("the client against ARES's OpenAPI document")
class AresSpecTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode SPEC =
            JSON.readTree(Path.of(Objects.requireNonNull(System.getProperty("ares.spec"), "ares.spec"))
                    .toFile());

    /** The endpoints the client calls, by the fixture prefix that stands for each. */
    private static final Map<String, String> ENDPOINTS = Map.of(
            "subject", "/ekonomicke-subjekty/{ico}",
            "nrpzs", "/ekonomicke-subjekty-nrpzs/{ico}",
            "rs", "/ekonomicke-subjekty-rs/{ico}",
            "rzp", "/ekonomicke-subjekty-rzp/{ico}");

    /** Each fixture, and the endpoint whose answer it stands for. */
    private static final Map<String, String> FIXTURES = Map.of(
            "company.json", "subject",
            "hospital.json", "subject",
            "school.json", "subject",
            "sole-trader.json", "subject",
            "nrpzs.json", "nrpzs",
            "rs.json", "rs",
            "rzp.json", "rzp");

    @Test
    @DisplayName("every field the client reads is in the document, with the type it reads it as")
    void fieldsTheClientReads() {
        var read = Map.ofEntries(
                // AresClient.company and address
                Map.entry("subject:ico", "string"),
                Map.entry("subject:obchodniJmeno", "string"),
                Map.entry("subject:dic", "string"),
                Map.entry("subject:pravniForma", "string"),
                Map.entry("subject:datumVzniku", "string"),
                Map.entry("subject:sidlo.kodStatu", "string"),
                Map.entry("subject:sidlo.nazevObce", "string"),
                Map.entry("subject:sidlo.nazevUlice", "string"),
                Map.entry("subject:sidlo.cisloDomovni", "integer"),
                Map.entry("subject:sidlo.cisloOrientacni", "integer"),
                Map.entry("subject:sidlo.cisloOrientacniPismeno", "string"),
                Map.entry("subject:sidlo.psc", "integer"),
                Map.entry("subject:sidlo.nazevMestskeCastiObvodu", "string"),
                Map.entry("subject:sidlo.textovaAdresa", "string"),
                Map.entry("subject:seznamRegistraci.stavZdrojeNrpzs", "string"),
                Map.entry("subject:seznamRegistraci.stavZdrojeRs", "string"),
                Map.entry("subject:seznamRegistraci.stavZdrojeRzp", "string"),
                // AresClient.contacts and primary; it reads an e-mail as a string or the first of an array, at both
                // endpoints
                Map.entry("nrpzs:zaznamy", "array"),
                Map.entry("nrpzs:zaznamy[].primarniZaznam", "boolean"),
                Map.entry("nrpzs:zaznamy[].kontakty.email", "string|array"),
                Map.entry("nrpzs:zaznamy[].kontakty.telefon", "string"),
                Map.entry("nrpzs:zaznamy[].kontakty.fax", "string"),
                Map.entry("nrpzs:zaznamy[].kontakty.www", "string"),
                Map.entry("rs:zaznamy[].primarniZaznam", "boolean"),
                Map.entry("rs:zaznamy[].kontakty.email", "string|array"),
                Map.entry("rs:zaznamy[].kontakty.telefon", "string"),
                // AresClient.soleTrader
                Map.entry("rzp:zaznamy[].primarniZaznam", "boolean"),
                Map.entry("rzp:zaznamy[].osobaPodnikatel.jmeno", "string"),
                Map.entry("rzp:zaznamy[].osobaPodnikatel.prijmeni", "string"),
                Map.entry("rzp:zaznamy[].osobaPodnikatel.datumNarozeni", "string"));

        List<String> wrong = new ArrayList<>();
        read.forEach((field, type) -> {
            var at = field.split(":", 2);
            var schema = at(response(at[0]), at[1]);
            var found = schema == null ? "missing" : typeOf(schema);
            if (!List.of(type.split("\\|", -1)).contains(found)) {
                wrong.add(field + " is " + found + ", the client reads " + type);
            }
        });
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    static Stream<String> fixtureNames() {
        return FIXTURES.keySet().stream().sorted();
    }

    @ParameterizedTest
    @MethodSource("fixtureNames")
    @DisplayName("a fixture holds only fields the document has, each of its type")
    void fixtures(String name) {
        var endpoint = Objects.requireNonNull(FIXTURES.get(name), name);
        List<String> wrong = new ArrayList<>();
        conforms(fixture(name), response(endpoint), "", wrong);
        assertTrue(wrong.isEmpty(), () -> name + ":\n" + String.join("\n", wrong));
    }

    @Test
    @DisplayName("each fixture is checked, so a new one cannot be left out")
    void everyFixtureIsChecked() throws IOException {
        var listed = new ArrayList<String>();
        try (var files = Files.list(Path.of(
                Objects.requireNonNull(AresSpecTest.class.getResource("/ares")).toURI()))) {
            files.map(f -> f.getFileName().toString())
                    .filter(f -> f.endsWith(".json"))
                    .forEach(listed::add);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(
                FIXTURES.keySet().stream().sorted().toList(),
                listed.stream().sorted().toList());
    }

    @Test
    @DisplayName("the walk finds a field behind a $ref and an allOf, and misses one that is not there")
    void walk() {
        assertEquals("integer", typeOf(Objects.requireNonNull(at(response("subject"), "sidlo.psc"))));
        assertNull(at(response("subject"), "sidlo.noSuchField"));
    }

    /** Recurses into objects and arrays; every name must exist where it is, and every value match its type. */
    private static void conforms(JsonNode value, JsonNode schema, String path, List<String> wrong) {
        var type = typeOf(schema);
        if (value.isNull()) {
            return;
        }
        if (!matches(value, type)) {
            wrong.add(path + " is " + value.getNodeType() + " in the fixture, " + type + " in the document");
            return;
        }
        if (value.isObject()) {
            for (var entry : value.properties()) {
                var child = property(schema, entry.getKey());
                var at = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                if (child == null) {
                    wrong.add(at + " is not in the document");
                } else {
                    conforms(entry.getValue(), child, at, wrong);
                }
            }
        } else if (value.isArray()) {
            var items = deref(schema).path("items");
            for (var element : value) {
                conforms(element, items, path + "[]", wrong);
            }
        }
    }

    private static boolean matches(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isString();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> false;
        };
    }

    /** The schema of a 200 answer of one of the client's endpoints. */
    private static JsonNode response(String endpoint) {
        var path = Objects.requireNonNull(ENDPOINTS.get(endpoint), endpoint);
        var schema = SPEC.path("paths")
                .path(path)
                .path("get")
                .path("responses")
                .path("200")
                .path("content")
                .path("application/json")
                .path("schema");
        assertTrue(!schema.isMissingNode(), () -> path + " has no JSON 200 answer in the document");
        return schema;
    }

    /** The schema at a dotted path, {@code []} stepping into an array's items; null when a step is missing. */
    private static @Nullable JsonNode at(JsonNode schema, String path) {
        @Nullable JsonNode current = schema;
        for (var step : path.split("\\.", -1)) {
            var items = step.endsWith("[]");
            var name = items ? step.substring(0, step.length() - 2) : step;
            current = property(Objects.requireNonNull(current), name);
            if (current != null && items) {
                current = deref(current).path("items");
            }
            if (current == null || current.isMissingNode()) {
                return null;
            }
        }
        return current;
    }

    /** A property of an object schema, looked for in its own properties and in every part of an {@code allOf}. */
    private static @Nullable JsonNode property(JsonNode schema, String name) {
        var resolved = deref(schema);
        var own = resolved.path("properties").path(name);
        if (!own.isMissingNode()) {
            return own;
        }
        for (var part : resolved.path("allOf")) {
            var found = property(part, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The type a schema declares, through {@code $ref} and {@code allOf}; an object when it only has properties.
     * {@code unknown} means the walk does not understand the schema ({@code oneOf}, {@code anyOf}, a 3.1 type list):
     * extend it here rather than loosen the check.
     */
    private static String typeOf(JsonNode schema) {
        var resolved = deref(schema);
        if (resolved.path("type").isString()) {
            return resolved.path("type").stringValue();
        }
        for (var part : resolved.path("allOf")) {
            var type = typeOf(part);
            if (!type.equals("unknown")) {
                return type;
            }
        }
        return resolved.has("properties") || resolved.has("allOf") ? "object" : "unknown";
    }

    private static JsonNode deref(JsonNode schema) {
        var current = schema;
        while (current.path("$ref").isString()) {
            var target = SPEC.at(current.path("$ref").stringValue().substring(1));
            assertNotNull(target, current.path("$ref").stringValue());
            current = target;
        }
        return current;
    }

    private static JsonNode fixture(String name) {
        try (InputStream in = AresSpecTest.class.getResourceAsStream("/ares/" + name)) {
            return JSON.readTree(Objects.requireNonNull(in, name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

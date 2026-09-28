package sg.securedhello.error;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders the error contract from {@link ErrorCode} (R-AUTH-003): the prose {@code docs/api/error-contract.md} and the
 * JSON Schema {@code docs/api/error-contract.schema.json}. The schema is what the backend's error bodies
 * (T-AUTH-011) and the SPA's MSW fixtures (T-AUTH-012) are validated against. {@link ErrorContractDriftIT} fails
 * {@code verify} when a committed rendering differs from these.
 */
public final class ErrorContract {

    /** The committed renderings, relative to the backend module (Maven's working directory for tests). */
    public static final Path DIRECTORY = Path.of("..", "docs", "api");
    public static final Path MARKDOWN = DIRECTORY.resolve("error-contract.md");
    public static final Path SCHEMA = DIRECTORY.resolve("error-contract.schema.json");

    private static final JsonMapper PRETTY = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            // Map.of iterates in a per-JVM order; sorted keys keep the committed rendering stable.
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private static final Schema COMPILED = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(jsonSchema(), InputFormat.JSON);

    private ErrorContract() {
    }

    /** The validation errors for {@code body} against the schema; empty when it conforms. */
    public static List<String> violations(String body) {
        return COMPILED.validate(body, InputFormat.JSON).stream().map(Object::toString).toList();
    }

    /** The JSON Schema every error body conforms to, generated from the enum. */
    public static String jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of("type", "string"));
        properties.put("title", Map.of("type", "string"));
        properties.put("status", Map.of("type", "integer"));
        properties.put("detail", Map.of("type", "string"));
        properties.put("instance", Map.of("type", "string"));
        properties.put("traceId", Map.of("type", "string", "pattern", "^[0-9a-f]{32}$"));
        properties.put("code", Map.of("enum", Arrays.stream(ErrorCode.values()).map(Enum::name).toList()));

        List<Object> pairings = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            Map<String, Object> then = new LinkedHashMap<>();
            then.put("type", Map.of("const", code.type()));
            then.put("title", Map.of("const", code.title()));
            then.put("status", Map.of("const", code.status()));
            then.put("detail", Map.of("const", code.detail()));
            pairings.add(Map.of(
                    "if", Map.of("properties", Map.of("code", Map.of("const", code.name()))),
                    "then", Map.of("properties", then)));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$id", "tag:securedhello.sg,2026:error-contract");
        schema.put("title", "Error envelope");
        schema.put("description", "Generated from sg.securedhello.error.ErrorCode. Do not edit; see error-contract.md.");
        schema.put("type", "object");
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        schema.put("allOf", pairings);
        return lf(PRETTY.writeValueAsString(schema)) + "\n";
    }

    /** The prose error contract, generated from the enum. */
    public static String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("""
                # Error contract

                <!-- Generated from sg.securedhello.error.ErrorCode by ErrorContractDriftIT. Do not edit by hand: change \
                the enum and run `mvn -f backend/pom.xml verify -Derror-contract.regenerate=true`. -->

                Every error the API returns is an RFC 9457 `application/problem+json` body with a `code` extension, \
                written only by `ProblemDetailWriter` (ADR-031). The machine-readable form of this contract is \
                [`error-contract.schema.json`](error-contract.schema.json).

                ## Envelope

                | Member | Type | Meaning |
                |---|---|---|
                | `type` | string | Derived from `code` for RFC 9457 conformance. Clients never read it. |
                | `title` | string | Constant per code. |
                | `status` | integer | The HTTP status; each code has exactly one. |
                | `detail` | string | Constant per code; never an exception message. |
                | `instance` | string | The request path that failed. |
                | `traceId` | string | 32 lowercase hex digits identifying the request. |
                | `code` | string | One value from the closed enum below. The only member a client branches on. |

                No other member is allowed until the enum declares it.

                ## Codes

                | `code` | Status | Title | Detail | Used for |
                |---|---|---|---|---|
                """);
        for (ErrorCode code : ErrorCode.values()) {
            md.append("| `%s` | %d | %s | %s | %s |%n".formatted(
                    code.name(), code.status(), code.title(), code.detail(), code.usage()));
        }
        md.append("""

                ## Rules

                - Clients branch on `code` and on nothing else (REJ-092).
                - `type` is `%s` followed by the code in lower case, with hyphens for underscores.
                - A status the producer knows without a code maps as: 400, 406, 413 and 415 to `VALIDATION_FAILED`; \
                401 to `AUTHENTICATION_FAILED`; 403, 404 and 405 to `ACCESS_DENIED`; 429 to `TOO_MANY_REQUESTS`; \
                anything else to `INTERNAL_ERROR`.
                - 401 responses carry no `WWW-Authenticate` challenge (R-AUTH-005).
                - `/actuator/**` is exempt: its health body is Actuator's own format (REJ-066).
                """.formatted(ErrorCode.TYPE_PREFIX));
        return lf(md.toString());
    }

    private static String lf(String text) {
        return text.replace("\r\n", "\n");
    }
}

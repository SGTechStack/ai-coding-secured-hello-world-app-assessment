package sg.securedhello.error;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import sg.securedhello.mfa.TotpFactorEntryPoint;
import sg.securedhello.mfa.TotpProblemAdvice;
import sg.securedhello.password.PasswordRule;

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

    /**
     * The extension members each code may carry, beyond the envelope: allowed on that code, with that code's values,
     * required there unless declared optional, and refused on every other code. A producer adds a member here, and the
     * renderings change with it.
     */
    static final Map<ErrorCode, Map<String, Extension>> EXTENSIONS = Map.of(
            ErrorCode.PASSWORD_REJECTED, Map.of("rule", new Extension(
                    Arrays.stream(PasswordRule.values()).map(Enum::name).toList(), true,
                    "The first password-policy rule the password failed, in the order the rules run (ADR-005).")),
            ErrorCode.VALIDATION_FAILED, Map.of("rule", new Extension(
                    Arrays.stream(ValidationRule.values()).map(Enum::name).toList(), false,
                    "Present only when the failure is a property of the submitted value the caller can act on: a "
                            + "taken username at registration (ADR-032). A format or length rejection carries none.")),
            ErrorCode.MISSING_FACTOR, Map.of(
                    "factor", new Extension(List.of(TotpFactorEntryPoint.FACTOR), true,
                            "The factor the admin surface requires (R-MFA-001)."),
                    "reason", new Extension(
                            Arrays.stream(TotpFactorEntryPoint.Reason.values()).map(Enum::name).toList(), true,
                            "`MISSING` when the session does not hold the factor; `EXPIRED` when it holds one older "
                                    + "than the rule accepts (ADR-021).")),
            ErrorCode.TOO_MANY_REQUESTS, Map.of(
                    "factor", new Extension(List.of(TotpFactorEntryPoint.FACTOR), false,
                            "Present only on a tier-1 factor lock, the factor that is locked; a source or identifier "
                                    + "throttle never carries it (ADR-033)."),
                    "reason", new Extension(List.of(TotpProblemAdvice.LOCKED), false,
                            "`LOCKED`, only with `factor` (ADR-027).")));

    /** An extension member on one code: its closed set of string values, whether it is required, what it means. */
    record Extension(List<String> values, boolean required, String meaning) {
    }

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
        List<String> envelope = List.copyOf(properties.keySet());
        // Each member's values across every code that carries it; each code narrows them to its own below.
        Map<String, Set<String>> allExtensions = new TreeMap<>();
        EXTENSIONS.values().forEach(members -> members.forEach((name, extension) ->
                allExtensions.computeIfAbsent(name, key -> new TreeSet<>()).addAll(extension.values())));
        allExtensions.forEach((name, values) -> properties.put(name, Map.of("enum", List.copyOf(values))));

        List<Object> pairings = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            Map<String, Object> then = new LinkedHashMap<>();
            then.put("type", Map.of("const", code.type()));
            then.put("title", Map.of("const", code.title()));
            then.put("status", Map.of("const", code.status()));
            then.put("detail", Map.of("const", code.detail()));
            // Each extension member: its values on its own code, where it is required, and false (absent) elsewhere.
            Map<String, Extension> own = EXTENSIONS.getOrDefault(code, Map.of());
            allExtensions.keySet().forEach(name -> then.put(name,
                    own.containsKey(name) ? Map.of("enum", own.get(name).values()) : false));
            Map<String, Object> constraints = new LinkedHashMap<>();
            constraints.put("properties", then);
            List<String> required = own.entrySet().stream().filter(entry -> entry.getValue().required())
                    .map(Map.Entry::getKey).sorted().toList();
            if (!required.isEmpty()) {
                constraints.put("required", required);
            }
            pairings.add(Map.of(
                    "if", Map.of("properties", Map.of("code", Map.of("const", code.name()))),
                    "then", constraints));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$id", "tag:securedhello.sg,2026:error-contract");
        schema.put("title", "Error envelope");
        schema.put("description", "Generated from sg.securedhello.error.ErrorCode. Do not edit; see error-contract.md.");
        schema.put("type", "object");
        schema.put("required", envelope);
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

                No other member is allowed until the contract declares it, as an extension member below.

                ## Codes

                | `code` | Status | Title | Detail | Used for |
                |---|---|---|---|---|
                """);
        for (ErrorCode code : ErrorCode.values()) {
            md.append("| `%s` | %d | %s | %s | %s |%n".formatted(
                    code.name(), code.status(), code.title(), code.detail(), code.usage()));
        }
        md.append("""

                ## Extension members

                Each is allowed only on the codes listed for it, with that code's values, and absent from every other.

                | Member | Code | Required | Values | Meaning |
                |---|---|---|---|---|
                """);
        EXTENSIONS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                new TreeMap<>(entry.getValue()).forEach((name, extension) ->
                        md.append(extensionRow(name, entry.getKey(), extension))));
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

    private static String extensionRow(String name, ErrorCode code, Extension extension) {
        List<String> values = extension.values().stream().map(value -> "`" + value + "`").toList();
        return "| `%s` | `%s` | %s | %s | %s |%n".formatted(name, code.name(), extension.required() ? "yes" : "no",
                String.join(", ", values), extension.meaning());
    }

    private static String lf(String text) {
        return text.replace("\r\n", "\n");
    }
}

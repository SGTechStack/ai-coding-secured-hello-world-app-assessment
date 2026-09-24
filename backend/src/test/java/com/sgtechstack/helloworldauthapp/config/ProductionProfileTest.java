package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the shape of the production profile in {@code application.yml}.
 *
 * <h2>Why parse the YAML instead of booting the profile</h2>
 *
 * Booting {@code prod} would be the stronger test and is not possible here: the
 * profile deliberately has no usable defaults, so its datasource placeholders are
 * unresolvable without a supplied datasource, and supplying one that points at
 * {@code mem:} to make the context start would test the exact configuration this
 * profile exists to prevent.
 *
 * <p>So this reads the document as data and asserts the properties that matter.
 * That is weaker than a running context, and it is exactly strong enough for the
 * failure mode worth defending against: somebody adding a convenient default to
 * get a deployment moving, and thereby turning "fails to start without
 * credentials" into "starts with whatever is lying around".
 *
 * <p>What this class cannot check — whether the migration is valid SQL and whether
 * {@code ddl-auto: validate} accepts it — no longer needs checking here. Since H2
 * is the only engine (ADR 0004), every {@code @SpringBootTest} in the suite builds
 * its schema from that migration and validates the entity model against it, so the
 * whole suite is the answer to that question.
 */
class ProductionProfileTest {

    private static Map<String, Object> baseDocument;
    private static Map<String, Object> devDocument;
    private static Map<String, Object> prodDocument;

    @BeforeAll
    static void loadDocuments() throws IOException {
        Path applicationYml = Paths.get("src", "main", "resources", "application.yml");
        assertThat(applicationYml)
                .as("test must run with the backend module as the working directory")
                .exists();

        List<Map<String, Object>> documents = new ArrayList<>();
        try (InputStream in = Files.newInputStream(applicationYml)) {
            new Yaml().loadAll(in).forEach(document -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> asMap = (Map<String, Object>) document;
                documents.add(asMap);
            });
        }

        baseDocument = documents.get(0);
        devDocument = documentForProfile(documents, "dev");
        prodDocument = documentForProfile(documents, "prod");
    }

    @Test
    void aProductionProfileExistsAtAll() {
        // The original finding: the only datasource in the application was the dev
        // one, so the app as committed could not start outside dev. Several
        // controls hardened earlier — HTTPS enforcement, CORS validation, Secure
        // cookies — were protecting a deployment that did not exist.
        assertThat(prodDocument).as("no prod profile document in application.yml").isNotNull();
    }

    @Test
    void theProductionDatasourceHasNoDefaultsToFallBackOn() {
        Map<String, Object> datasource = nested(prodDocument, "spring", "datasource");

        // Every value an unresolvable placeholder. A default here would mean a
        // deployment that forgot a variable starts anyway — in the datasource's
        // case, quite possibly against an in-memory database that accepts writes
        // and loses them, which is the failure mode where everything looks healthy
        // and nothing is persisted.
        assertThat(String.valueOf(datasource.get("url"))).isEqualTo("${APP_DB_URL}");
        assertThat(String.valueOf(datasource.get("username"))).isEqualTo("${APP_DB_USERNAME}");
        assertThat(String.valueOf(datasource.get("password"))).isEqualTo("${APP_DB_PASSWORD}");
    }

    @Test
    void noCredentialIsCommittedInTheProductionProfile() {
        // Guards against the specific accident of pasting a working value in to
        // test something and leaving it. Placeholders contain ${...}; a literal
        // would not.
        Map<String, Object> datasource = nested(prodDocument, "spring", "datasource");

        assertThat(datasource.get("password").toString()).startsWith("${");
        assertThat(datasource.get("username").toString()).startsWith("${");
        // The dev datasource's blank-password `sa` must not appear here. An
        // embedded H2 file with no password is readable by any process that can
        // open the file.
        assertThat(datasource.get("username").toString()).isNotEqualTo("sa");
    }

    @Test
    void theProductionDatabaseIsPersistentNotInMemory() {
        // With one engine everywhere, the difference between a real deployment and
        // a throwaway one is a single word in the URL. `mem:` would accept every
        // write and lose them on restart, which is the failure mode where
        // everything looks healthy and nothing is persisted - so the URL must come
        // from the environment and never be defaulted.
        assertThat(String.valueOf(nested(prodDocument, "spring", "datasource").get("url")))
                .isEqualTo("${APP_DB_URL}")
                .doesNotContain("mem:");
    }

    @Test
    void hibernateValidatesTheSchemaRatherThanAlteringIt() {
        Object ddlAuto = nested(prodDocument, "spring", "jpa", "hibernate").get("ddl-auto");

        // `update` lets the running application alter the schema — an ambient
        // write privilege over the data model, and a silent divergence between
        // environments with no record of the change. `validate` turns drift into a
        // startup failure instead of a data problem found later.
        assertThat(ddlAuto).isEqualTo("validate");
    }

    @Test
    void noProfileUsesDdlAutoUpdateAnyMore() {
        // Dev used to, because it built its schema from the entity model and never
        // ran the migrations. Standardising on one engine removed the reason: dev
        // now migrates like everything else and validates the result, so a field
        // added without a migration fails on the next test run instead of
        // surviving to a deployment.
        assertThat(nested(devDocument, "spring", "jpa", "hibernate").get("ddl-auto"))
                .isEqualTo("validate");
        assertThat(nested(prodDocument, "spring", "jpa", "hibernate").get("ddl-auto"))
                .isEqualTo("validate");
        assertThat(nested(baseDocument, "spring", "jpa", "hibernate")).isEmpty();
    }

    @Test
    void migrationsRunInEveryProfile() {
        assertThat(nested(prodDocument, "spring", "flyway").get("enabled")).isEqualTo(true);

        // Base enables them, so the default for any new profile is "migrations
        // run" rather than "Hibernate improvises a schema", and dev no longer
        // opts out.
        assertThat(String.valueOf(nested(baseDocument, "spring", "flyway").get("enabled")))
                .isEqualTo("${SPRING_FLYWAY_ENABLED:true}");
        assertThat(nested(devDocument, "spring", "flyway"))
                .as("dev must not disable migrations; running them is what exercises them")
                .isEmpty();
    }

    @Test
    void enumsAreMappedToVarcharSoTheSchemaDoesNotPinTheValueList() {
        // Left to itself, Hibernate expects H2's native ENUM ('ADMIN','USER')
        // column type, which bakes the vocabulary into the schema: adding a Role
        // or AuditAction constant would need a migration to alter the column. The
        // migration is written with varchar, so this property is what keeps
        // `validate` agreeing with it.
        assertThat(nested(baseDocument, "spring", "jpa", "properties", "hibernate", "type")
                .get("preferred_enum_jdbc_type"))
                .isEqualTo("VARCHAR");
    }

    @Test
    void theDevAdminPasswordIsTheAgreedFixedValue() {
        // A product decision, and a deliberate reopening of TM-07: this exact
        // credential was a High finding, because it was published and prefilled.
        // Pinned here so the decision is visible in one place rather than being
        // rediscovered from a log line.
        assertThat(String.valueOf(nested(devDocument, "app", "admin").get("password")))
                .isEqualTo("${APP_ADMIN_PASSWORD:password1234}");
    }

    @Test
    void theFixedAdminPasswordCannotLeakOutsideDev() {
        // The guard rail that keeps the decision above scoped. The base profile's
        // placeholder has no default at all, so a deployment that forgets
        // APP_ADMIN_PASSWORD fails to start instead of quietly seeding an admin
        // account whose password is published in this repository.
        assertThat(String.valueOf(nested(baseDocument, "app", "admin").get("password")))
                .isEqualTo("${APP_ADMIN_PASSWORD}")
                .doesNotContain("password1234");
        assertThat(String.valueOf(nested(baseDocument, "app", "admin").get("username")))
                .isEqualTo("${APP_ADMIN_USERNAME}");
    }

    @Test
    void migrationsAreNotBaselinedOverAnExistingSchema() {
        // baseline-on-migrate would make Flyway assume whatever tables it finds
        // are the baseline, so pointing a fresh deployment at a populated database
        // would silently skip every migration. Failing loudly is correct.
        assertThat(nested(prodDocument, "spring", "flyway").get("baseline-on-migrate"))
                .isEqualTo(false);
        assertThat(nested(prodDocument, "spring", "flyway").get("validate-on-migrate"))
                .isEqualTo(true);
    }

    @Test
    void theH2ConsoleIsExplicitlyOffInProduction() {
        assertThat(nested(prodDocument, "spring", "h2", "console").get("enabled")).isEqualTo(false);
    }

    @Test
    void theSessionCookieIsSecureByDefaultAndOnlyDevOptsOut() {
        // The other half of SessionCookieAttributesTest, which can only observe
        // dev. Together they pin that the dev concession stays a concession.
        assertThat(nested(baseDocument, "server", "servlet", "session", "cookie").get("secure"))
                .isEqualTo(true);
        assertThat(nested(devDocument, "server", "servlet", "session", "cookie").get("secure"))
                .isEqualTo(false);
    }

    @Test
    void productionLogsAreStructuredAndRetentionIsBounded() {
        Map<String, Object> logging = asMap(prodDocument.get("logging"));

        // Structured output is what makes the log queryable without a parser, and
        // what makes a newline inside a value harmless: the encoder escapes it, so
        // forging cannot be achieved by content alone.
        assertThat(nested(logging, "structured", "format").get("console")).isEqualTo("ecs");
        assertThat(nested(logging, "structured", "format").get("file")).isEqualTo("ecs");

        // Retention enforced rather than described. Without a cap, the rotation
        // policy is "forever", which was the finding.
        assertThat(String.valueOf(nested(logging, "logback", "rollingpolicy").get("max-history")))
                .isEqualTo("${APP_LOG_MAX_HISTORY:90}");
        assertThat(nested(logging, "logback", "rollingpolicy")).containsKey("total-size-cap");
    }

    @Test
    void theStatedLogRetentionMatchesThePrivacyNotice() {
        // The notice tells users security logs are kept 90 days. If the two ever
        // disagree, one of them is a false statement to a data subject — and the
        // notice is the one people read.
        @SuppressWarnings("unchecked")
        List<String> retention = (List<String>) nested(baseDocument, "app", "privacy").get("retention");

        assertThat(retention)
                .as("the notice must state a log retention period, and it must be the configured one")
                .anySatisfy(line -> assertThat(line).contains("90 days"));
    }

    private static Map<String, Object> documentForProfile(List<Map<String, Object>> documents, String profile) {
        return documents.stream()
                .filter(document -> profile.equals(activatedProfile(document)))
                .findFirst()
                .orElse(null);
    }

    private static String activatedProfile(Map<String, Object> document) {
        return Optional.ofNullable(asMap(document.get("spring")))
                .map(spring -> asMap(spring.get("config")))
                .map(config -> asMap(config.get("activate")))
                .map(activate -> activate.get("on-profile"))
                .map(String::valueOf)
                .orElse(null);
    }

    /** Walks a nested map path, answering an empty map rather than null. */
    private static Map<String, Object> nested(Map<String, Object> root, String... path) {
        Map<String, Object> current = root;
        for (String key : path) {
            current = asMap(current.get(key));
            if (current == null) {
                return Map.of();
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}

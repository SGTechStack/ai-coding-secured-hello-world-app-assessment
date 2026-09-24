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
 * unresolvable without an external database, and supplying an H2 one to make the
 * context start would test a configuration nobody deploys. This repository has no
 * PostgreSQL to point at, and pretending otherwise would produce a test that
 * passes while proving nothing.
 *
 * <p>So this reads the document as data and asserts the properties that matter.
 * That is weaker than a running context, and it is exactly strong enough for the
 * failure mode worth defending against: somebody adding a convenient default to
 * get a deployment moving, and thereby turning "fails to start without
 * credentials" into "starts with whatever is lying around".
 *
 * <p>What this cannot check is whether the migration is valid PostgreSQL, or
 * whether {@code ddl-auto: validate} accepts it. That needs a real engine, and is
 * recorded as outstanding in the threat model rather than implied to be covered.
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
        String rendered = String.valueOf(prodDocument);

        // Guards against the specific accident of pasting a working value in to
        // test something and leaving it. Placeholders contain ${...}; a literal
        // would not.
        assertThat(rendered).doesNotContain("sa\"").doesNotContain("postgres:postgres");
        assertThat(nested(prodDocument, "spring", "datasource").get("password").toString())
                .startsWith("${");
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
    void devIsTheOnlyProfileThatUsesDdlAutoUpdate() {
        assertThat(nested(devDocument, "spring", "jpa", "hibernate").get("ddl-auto"))
                .isEqualTo("update");
        // The base document must not set it at all, so nothing inherits `update`
        // by accident.
        assertThat(nested(baseDocument, "spring", "jpa")).isEmpty();
    }

    @Test
    void migrationsAreEnabledInProductionAndDisabledOnlyInDev() {
        assertThat(nested(prodDocument, "spring", "flyway").get("enabled")).isEqualTo(true);
        assertThat(nested(devDocument, "spring", "flyway").get("enabled")).isEqualTo(false);

        // Base enables them, so the default for any new profile is "migrations
        // run" rather than "Hibernate improvises a schema".
        assertThat(String.valueOf(nested(baseDocument, "spring", "flyway").get("enabled")))
                .isEqualTo("${SPRING_FLYWAY_ENABLED:true}");
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

package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.Column;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.user.UserAccount;

/**
 * The negative half of the schema gate (ADR-051): each deliberate drift must make startup fail in Hibernate's schema
 * validation. Each test owns one H2 file and starts on it twice ({@code restart}, {@code own-DB}): once to migrate and
 * drift it, once to prove the drift is caught. Only the persistence auto-configuration runs, with the production
 * {@code application.yml}, so a failure here can only come from the schema.
 */
class SchemaValidationGateTest {

    @TempDir
    Path directory;

    @Test
    @Proves("T-CFG-002")
    void aUuidColumnRetypedToVarcharFails() {
        assertThat(startupFailureAfter("ALTER TABLE deleted_users ALTER COLUMN deleted_by_id VARCHAR(36)"))
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .rootCause()
                .message().contains("wrong column type").containsIgnoringCase("deleted_by_id");
    }

    @Test
    @Proves("T-CFG-003")
    void totpKeyRetypedToBinaryFails() {
        assertThat(startupFailureAfter("ALTER TABLE totp_user_details ALTER COLUMN totp_key BINARY(69)"))
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .rootCause()
                .message().contains("wrong column type").containsIgnoringCase("totp_key");
    }

    @Test
    @Proves("T-CFG-004")
    void aDroppedUniqueIndexFails() {
        assertThat(startupFailureAfter("DROP INDEX ux_users_username"))
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .rootCause()
                .hasMessageContaining("Missing unique constraint named `ux_users_username`");
    }

    @Test
    @Proves("T-CFG-005")
    void reorderedCompositeIndexColumnsFail() {
        assertThat(startupFailureAfter(
                "ALTER TABLE credential_tokens DROP CONSTRAINT fk_credential_tokens_user_id",
                "DROP INDEX ix_credential_tokens_user_id_type",
                "CREATE INDEX ix_credential_tokens_user_id_type ON credential_tokens (type, user_id)"))
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .rootCause()
                .hasMessageContaining("Index mismatch - `ix_credential_tokens_user_id_type`");
    }

    @Test
    @Proves("T-CFG-006")
    void lowercaseMappedNamesPassAgainstUpperCasedMetadata() throws NoSuchFieldException {
        assertThat(UserAccount.class.getDeclaredField("username").getAnnotation(Column.class).name())
                .isEqualTo("username");

        AtomicReference<String> metadataName = new AtomicReference<>();
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            metadataName.set(new JdbcTemplate(context.getBean(DataSource.class)).queryForObject(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'USERS'"
                            + " AND COLUMN_NAME = UPPER('username')", String.class));
        });
        assertThat(metadataName.get()).isEqualTo("USERNAME");

        assertThat(startupFailureAfter()).isNull();
    }

    /** Migrates a fresh file, applies {@code drift}, restarts on the same file and returns the startup failure. */
    private Throwable startupFailureAfter(String... drift) {
        runner().run(context -> {
            assertThat(context).as("the undrifted schema validates").hasNotFailed();
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            for (String statement : drift) {
                jdbc.execute(statement);
            }
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runner().run(context -> failure.set(context.getStartupFailure()));
        return failure.get();
    }

    private ApplicationContextRunner runner() {
        String file = directory.resolve("gate").toString().replace('\\', '/');
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.datasource.url=jdbc:h2:file:" + file + ";LOCK_TIMEOUT=1000")
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                .withUserConfiguration(Entities.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = SecuredHelloApplication.class)
    static class Entities {
    }
}

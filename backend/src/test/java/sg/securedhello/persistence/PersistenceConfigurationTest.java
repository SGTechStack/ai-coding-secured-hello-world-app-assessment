package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.CtxNondevTest;

/** The production persistence settings the schema gate relies on (ADR-030; ADR-051; REJ-040; REJ-041). */
class PersistenceConfigurationTest extends CtxNondevTest {

    @Test
    void hibernateOnlyValidatesWithNamedIndexAndUniqueKeyValidation() {
        assertThat(productionProperty("spring.jpa.hibernate.ddl-auto", String.class)).isEqualTo("validate");
        // Raw Hibernate keys contain underscores, which the Binder does not accept, so read them from the environment.
        assertThat(productionEnvironment().getProperty("spring.jpa.properties.hibernate.tooling.schema.index_validation"))
                .isEqualTo("NAMED");
        assertThat(productionEnvironment().getProperty(
                "spring.jpa.properties.hibernate.tooling.schema.unique_key_validation")).isEqualTo("NAMED");
    }

    @Test
    void flywayCleanIsDisabled() {
        assertThat(productionProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
    }

    @Test
    void springSessionNeverCreatesItsOwnSchema() {
        assertThat(productionProperty("spring.session.jdbc.initialize-schema", String.class)).isEqualTo("never");
    }

    @Test
    void theDatasourceIsAnH2FileWithoutFileLockNo() {
        assertThat(productionProperty("spring.datasource.url", String.class))
                .startsWith("jdbc:h2:file:")
                .contains(";LOCK_TIMEOUT=1000")
                .doesNotContainIgnoringCase("FILE_LOCK=NO");
    }
}

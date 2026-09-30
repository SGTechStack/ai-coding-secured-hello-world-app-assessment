package sg.securedhello.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

/**
 * {@code app.security.roles} is the role set's source of truth, and startup refuses a declaration the seeded
 * {@code roles} table disagrees with, or one that names a role twice, before the port opens (ADR-042).
 */
class RoleDefinitionsRestartTest {

    @TempDir
    Path database;

    private Boot boot(Consumer<ConfigurableApplicationContext> action, String... args) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database)),
                action, args);
    }

    @Test
    @Proves("T-ADM-011")
    void aRoleDeclaredButNotStoredRefusesStartup() {
        Boot boot = boot(context -> { }, "--app.security.roles[0]=USER", "--app.security.roles[1]=ADMIN",
                "--app.security.roles[2]=AUDITOR");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("app.security.roles and the roles table disagree",
                "declared only [AUDITOR]");
    }

    @Test
    @Proves("T-ADM-011")
    void aRoleStoredButNotDeclaredRefusesStartup() {
        Boot boot = boot(context -> { }, "--app.security.roles[0]=ADMIN");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("stored only [USER]");
    }

    @Test
    @Proves("T-ADM-011")
    void aTableThatDriftedFromTheDeclarationRefusesTheNextStartup() {
        Boot first = boot(context -> context.getBean(JdbcTemplate.class)
                .update("INSERT INTO roles (name) VALUES ('AUDITOR')"));
        assertThat(first.failure()).isNull();
        assertThat(first.portOpened()).isTrue();

        Boot second = boot(context -> { });

        assertThat(second.portOpened()).isFalse();
        assertThat(second.failureMessages()).contains("stored only [AUDITOR]");
    }

    @Test
    @Proves("T-ADM-018")
    void aDuplicatedRoleNameRefusesStartupRatherThanCollapsing() {
        Boot boot = boot(context -> { }, "--app.security.roles[0]=USER", "--app.security.roles[1]=ADMIN",
                "--app.security.roles[2]=ADMIN");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("declares [ADMIN] more than once");
    }
}

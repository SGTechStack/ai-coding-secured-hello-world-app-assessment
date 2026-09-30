package sg.securedhello.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The role set's refresh-phase validator (ADR-042). {@code app.security.roles} is the source of truth, and the seeded,
 * read-only {@code roles} table exists so {@code users.role} can be a foreign key. Startup is refused, before the web
 * server opens its port, when:
 * <ul>
 *   <li>a role name is declared twice, which would otherwise collapse silently (Std §5:424; T-ADM-018);</li>
 *   <li>the declaration and the table disagree, a role missing on either side (R-DATA-003; T-ADM-011).</li>
 * </ul>
 * It runs once every singleton exists, so after Flyway has migrated, and before the context finishes refreshing.
 */
@Component
class RoleDefinitions implements SmartInitializingSingleton {

    static final String PROPERTY = "app.security.roles";

    private final Environment environment;
    private final JdbcTemplate jdbc;

    RoleDefinitions(Environment environment, JdbcTemplate jdbc) {
        this.environment = environment;
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> declared = Binder.get(environment).bind(PROPERTY, Bindable.listOf(String.class))
                .orElseThrow(() -> new IllegalStateException("Startup refused: " + PROPERTY + " is not set (ADR-042)"));
        Set<String> seen = new HashSet<>();
        Set<String> duplicated = new TreeSet<>();
        declared.forEach(role -> {
            if (!seen.add(role)) {
                duplicated.add(role);
            }
        });
        if (!duplicated.isEmpty()) {
            throw new IllegalStateException("Startup refused: " + PROPERTY + " declares " + duplicated
                    + " more than once (ADR-042)");
        }
        Set<String> stored = new TreeSet<>(jdbc.queryForList("SELECT name FROM roles", String.class));
        if (!stored.equals(new TreeSet<>(declared))) {
            Set<String> onlyDeclared = new TreeSet<>(declared);
            onlyDeclared.removeAll(stored);
            Set<String> onlyStored = new TreeSet<>(stored);
            onlyStored.removeAll(declared);
            throw new IllegalStateException("Startup refused: " + PROPERTY + " and the roles table disagree: declared"
                    + " only " + onlyDeclared + ", stored only " + onlyStored + " (ADR-042)");
        }
    }
}

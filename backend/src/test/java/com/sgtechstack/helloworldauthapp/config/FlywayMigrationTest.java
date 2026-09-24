package com.sgtechstack.helloworldauthapp.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the checked-in migration and the entity model in step.
 *
 * <h2>What this is for, now that the migration actually runs</h2>
 *
 * This class was written when dev built its schema from the entity model and the
 * migration was never executed anywhere, which made it the only line of defence
 * against the realistic mistake: adding a field to an entity, watching dev work
 * perfectly, and never touching the SQL.
 *
 * <p>Since H2 became the only engine (ADR 0004), Flyway runs in dev and in every
 * {@code @SpringBootTest} here, with {@code ddl-auto: validate} checking the entity
 * model against the result. So that mistake is now caught by the suite at large,
 * and this class is no longer load-bearing.
 *
 * <p>It is kept because it still earns its place two ways. It fails faster and with
 * a far clearer message than a Hibernate validation error — naming the missing
 * column and the entity field it came from. And its remaining assertions are about
 * properties {@code validate} does not check at all: that migrations are named so
 * Flyway will actually pick them up, that the audit table has no foreign key to
 * users, and that the case-insensitive uniqueness indexes exist.
 *
 * <p>Reflection rather than a hardcoded list on purpose: a hardcoded list is a
 * third place to forget to update, and would pass while being just as stale as the
 * SQL.
 */
class FlywayMigrationTest {

    private static final String ENTITY_PACKAGE = "com.sgtechstack.helloworldauthapp";

    private static String migrationSql;
    private static List<Class<?>> entities;

    @BeforeAll
    static void load() throws IOException {
        Path migrationDir = Paths.get("src", "main", "resources", "db", "migration");
        assertThat(migrationDir)
                .as("test must run with the backend module as the working directory")
                .exists();

        StringBuilder combined = new StringBuilder();
        try (Stream<Path> files = Files.list(migrationDir)) {
            for (Path file : files.sorted().toList()) {
                combined.append(Files.readString(file)).append('\n');
            }
        }
        migrationSql = combined.toString().toLowerCase(Locale.ROOT);

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        entities = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            try {
                entities.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new AssertionError("scanned an entity that could not be loaded", e);
            }
        }
    }

    @Test
    void thereIsAtLeastOneVersionedMigration() throws IOException {
        try (Stream<Path> files = Files.list(Paths.get("src", "main", "resources", "db", "migration"))) {
            List<String> names = files.map(path -> path.getFileName().toString()).toList();

            assertThat(names).isNotEmpty();
            // Flyway ignores a file that does not match V<version>__<description>.sql,
            // silently, so a misnamed migration is a migration that never runs.
            assertThat(names).allSatisfy(name -> assertThat(name).matches("V\\d+(\\.\\d+)*__.+\\.sql"));
        }
    }

    @Test
    void everyEntityHasATableInTheMigration() {
        assertThat(entities)
                .as("entity scan found nothing, so this test would pass vacuously")
                .isNotEmpty();

        for (Class<?> entity : entities) {
            String table = tableNameOf(entity);

            assertThat(migrationSql)
                    .as("no CREATE TABLE for entity %s (expected table '%s')", entity.getSimpleName(), table)
                    .contains("create table " + table);
        }
    }

    @Test
    void everyPersistedColumnAppearsInTheMigration() {
        for (Class<?> entity : entities) {
            for (Field field : entity.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                String column = columnNameOf(field);

                assertThat(migrationSql)
                        .as("column '%s' (from %s.%s) is missing from the migration — an entity field was "
                                        + "added without a migration, which dev cannot reveal because it uses "
                                        + "ddl-auto: update",
                                column, entity.getSimpleName(), field.getName())
                        .contains(column);
            }
        }
    }

    @Test
    void theAuditTableHasNoForeignKeyToUsers() {
        // Load-bearing for the delete case. A foreign key would force the audit row
        // to either cascade away with the account — destroying the evidence that
        // the deletion happened — or block the deletion outright. The record has to
        // outlive its subject.
        int auditTableStart = migrationSql.indexOf("create table admin_audit_log");
        assertThat(auditTableStart).isNotNegative();

        String auditTable = migrationSql.substring(auditTableStart,
                migrationSql.indexOf(");", auditTableStart));

        assertThat(auditTable)
                .as("an audit record must survive the deletion of the account it describes")
                .doesNotContain("references users");
    }

    @Test
    void caseInsensitiveLookupsAreBackedByCaseInsensitiveUniqueness() {
        // The repository looks accounts up with findByUsernameIgnoreCase and
        // findByEmailIgnoreCase, so a plain UNIQUE constraint does not cover them:
        // 'Alice' and 'alice' are two permitted rows that both answer the same
        // lookup, and which one wins is arbitrary.
        assertThat(migrationSql).contains("lower(username)");
        assertThat(migrationSql).contains("lower(email)");
    }

    private static String tableNameOf(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name().toLowerCase(Locale.ROOT);
        }
        return entity.getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static String columnNameOf(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name().toLowerCase(Locale.ROOT);
        }

        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isBlank()) {
            return joinColumn.name().toLowerCase(Locale.ROOT);
        }

        // Hibernate's default naming strategy turns camelCase into snake_case.
        return field.getName()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}

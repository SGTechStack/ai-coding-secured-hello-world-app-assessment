package sg.securedhello.persistence;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Boot's default naming, with unquoted column names rendered in the case the database stores them (upper case on H2).
 *
 * <p>Hibernate's {@code NAMED} index and unique-key validation compares each mapped column name with the metadata's
 * column name case-sensitively, so a lowercase mapping fails against H2's upper-cased metadata although the schema is
 * right (ADR-051; T-CFG-006). Unquoted identifiers are case-insensitive in SQL, so the SQL Hibernate emits is unchanged.
 */
public class MetadataCaseNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {

    @Override
    public Identifier toPhysicalColumnName(Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
        Identifier name = super.toPhysicalColumnName(logicalName, jdbcEnvironment);
        if (name == null || name.isQuoted()) {
            return name;
        }
        return Identifier.toIdentifier(jdbcEnvironment.getIdentifierHelper().toMetaDataObjectName(name));
    }
}

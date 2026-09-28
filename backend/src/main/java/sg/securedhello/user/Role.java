package sg.securedhello.user;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A role definition. The table is seeded by V1 and never written at runtime; {@code app.security.roles} is the source
 * of truth, and this table exists so {@code users.role} can be a foreign key (ADR-042).
 */
@Entity
@Immutable
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "name", length = 20)
    private String name;

    protected Role() {
    }

    public String getName() {
        return name;
    }
}

package sg.securedhello.mfa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Pending enrolments, keyed on the user's id (ADR-053). */
public interface PendingTotpRepository extends JpaRepository<PendingTotp, UUID> {
}

package sg.securedhello.mfa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Confirmed factors, keyed on the user's id. A row's existence is the enrolment (ADR-053). */
public interface TotpUserDetailsRepository extends JpaRepository<TotpUserDetails, UUID> {
}

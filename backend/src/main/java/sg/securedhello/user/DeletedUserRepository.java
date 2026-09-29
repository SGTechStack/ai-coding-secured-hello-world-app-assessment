package sg.securedhello.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Deleted-user tombstones (ADR-044), looked up by canonical username or by the email's keyed hash. */
public interface DeletedUserRepository extends JpaRepository<DeletedUser, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmailHmac(String emailHmac);
}

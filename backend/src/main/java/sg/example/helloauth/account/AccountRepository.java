package sg.example.helloauth.account;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Tombstones count: their usernames and emails stay reserved. */
    boolean existsByUsernameKeyOrEmail(String usernameKey, String email);

    Optional<Account> findByUsernameKeyAndDeletedAtIsNull(String usernameKey);

    /** Holds the row until the transaction ends, so concurrent failed logins are all counted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateByUsernameKeyAndDeletedAtIsNull(String usernameKey);

    Optional<Account> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateByEmailAndDeletedAtIsNull(String email);

    boolean existsByRoleAndDeletedAtIsNull(Role role);

    /** Oldest first; Accounts created at the same instant in username order, so pages are stable. */
    Page<Account> findByDeletedAtIsNullOrderByCreatedAtAscUsernameKeyAsc(Pageable pageable);
}

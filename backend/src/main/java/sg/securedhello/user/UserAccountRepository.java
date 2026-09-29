package sg.securedhello.user;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accounts, by id or by their canonical username (ADR-045). */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsername(String username);

    /**
     * The account, with its row locked for the rest of the transaction ({@code SELECT ... FOR UPDATE}), so concurrent
     * sign-in outcomes on one account are counted one after another (R-DATA-014).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.username = :username")
    Optional<UserAccount> findForUpdateByUsername(@Param("username") String username);

    /** As {@link #findForUpdateByUsername}, by id. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.id = :id")
    Optional<UserAccount> findForUpdateById(@Param("id") UUID id);
}

package sg.securedhello.registration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Username holds (ADR-032), by canonical username. */
interface UsernameHoldRepository extends JpaRepository<UsernameHold, UUID> {

    Optional<UsernameHold> findByUsername(String username);

    /** Removes every hold that has run out by {@code now}, so an expired hold never blocks and none accumulate. */
    @Modifying
    @Query("delete from UsernameHold hold where hold.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}

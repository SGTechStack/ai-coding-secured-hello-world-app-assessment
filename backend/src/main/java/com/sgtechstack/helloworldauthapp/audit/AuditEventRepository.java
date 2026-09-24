package com.sgtechstack.helloworldauthapp.audit;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately extends {@link Repository} — the empty marker interface — rather
 * than {@code JpaRepository}.
 *
 * <p>{@code JpaRepository} would hand this table {@code delete},
 * {@code deleteAll}, {@code deleteById} and {@code saveAll} for free. On an
 * append-only audit log those are not conveniences, they are the operations the
 * log exists to prevent, and inheriting them would mean the append-only
 * property held only for as long as nobody typed {@code auditRepository.delete}
 * and got a green build.
 *
 * <p>Spring Data generates an implementation for exactly the methods declared
 * here, so the absence of a delete method is the absence of a delete
 * capability. Adding one is then a visible line in a diff with nowhere to hide,
 * which is the review moment this design is buying.
 *
 * <p>The query methods are read-only and exist for operators and tests. They
 * are derived from method names, keeping this repository consistent with every
 * other one in the application — no {@code @Query}, no native SQL, nothing that
 * concatenates a string into a statement.
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findAllByOrderByOccurredAtAsc();

    List<AuditEvent> findAllByActionOrderByOccurredAtAsc(AuditAction action);

    List<AuditEvent> findAllByTargetIdOrderByOccurredAtAsc(UUID targetId);

    long count();
}

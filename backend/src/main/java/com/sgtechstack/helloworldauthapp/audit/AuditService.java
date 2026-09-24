package com.sgtechstack.helloworldauthapp.audit;

import com.sgtechstack.helloworldauthapp.logging.LogSafe;
import com.sgtechstack.helloworldauthapp.logging.LoggingContext;
import com.sgtechstack.helloworldauthapp.logging.UserPseudonym;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes audit records for consequential actions.
 *
 * <h2>Why {@code Propagation.MANDATORY}</h2>
 *
 * The finding this class answers was that destructive admin actions left
 * nothing behind but an unstructured INFO line. A log line is a side channel:
 * the mutation commits, the line is written, and if the process dies between
 * them — or the transaction rolls back after it — the two disagree, with no way
 * to tell which happened. An audit record is only evidence if it shares the
 * fate of the thing it describes.
 *
 * <p>{@code MANDATORY} enforces that. It does not start a transaction; it
 * refuses to run outside one. So a future caller who audits from a
 * non-transactional context fails loudly at that call rather than quietly
 * writing a record that commits independently of the action, which is the
 * failure mode that would reintroduce the original problem while looking like
 * a fix.
 *
 * <h2>What is and is not recorded</h2>
 *
 * Usernames are reduced to pseudonymous references (see {@link UserPseudonym}).
 * The record still answers "was this the same account as that other record",
 * which is what an investigation needs, without the audit table becoming a
 * second permanent copy of who-is-who. Resolving a reference back to a person
 * requires the key and a deliberate act.
 *
 * <p>The mirrored log line exists on purpose alongside the row. The table is
 * the authoritative record; the line is what gets shipped to an aggregator and
 * alerted on. It carries the same reference and the same correlation id, so the
 * two can be joined without either being a substitute for the other.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final UserPseudonym pseudonym;

    public AuditService(AuditEventRepository repository, UserPseudonym pseudonym) {
        this.repository = repository;
        this.pseudonym = pseudonym;
    }

    /**
     * Records {@code action} against the caller's transaction.
     *
     * @param actorId        id of the account taking the action, or null when
     *                       the system took it
     * @param actorUsername  used only to derive the pseudonymous reference; not
     *                       stored
     * @param targetId       id of the affected account, or null when the action
     *                       has no distinct target
     * @param targetUsername used only to derive the pseudonymous reference; not
     *                       stored
     * @param detail         what changed, or the stated purpose of a read
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         if called outside a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(
            AuditAction action,
            UUID actorId,
            String actorUsername,
            UUID targetId,
            String targetUsername,
            String detail
    ) {
        String actorRef = pseudonym.of(actorUsername);
        String targetRef = pseudonym.of(targetUsername);
        String clientIp = LoggingContext.clientIp();
        String requestId = LoggingContext.requestId();

        AuditEvent event = repository.save(new AuditEvent(
                action, actorId, actorRef, targetId, targetRef, clientIp, requestId, LogSafe.value(detail)));

        log.info("Audit action={} actorRef={} targetRef={} clientIp={} requestId={} detail={}",
                action, actorRef, targetRef, clientIp, requestId, LogSafe.value(detail));

        return event;
    }
}

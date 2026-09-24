package com.sgtechstack.helloworldauthapp.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One record of a consequential action, written in the same transaction as the
 * action itself.
 *
 * <h2>Append-only by construction</h2>
 *
 * There are no setters and no public no-arg constructor. Every field is
 * assigned once, at construction, and {@code @Column(updatable = false)} means
 * Hibernate will not emit an {@code UPDATE} for any of them even if a
 * reflective or future change tried. {@link AuditEventRepository} exposes no
 * delete or update operation either. So "append-only" is a property of the code
 * rather than a convention someone has to remember.
 *
 * <p>This is application-level immutability, and worth being precise about what
 * it does and does not buy. It stops the application from rewriting history,
 * including via a bug or a compromised endpoint. It does <em>not</em> stop
 * anyone with direct database credentials, because nothing at this layer can:
 * that requires a database role with {@code INSERT} but not {@code UPDATE} or
 * {@code DELETE} on this table, which is a deployment-time grant. See
 * {@code docs/adr/0007-append-only-admin-audit-log.md}.
 *
 * <h2>No foreign key to the user table</h2>
 *
 * {@code actorId} and {@code targetId} are plain UUID columns, not
 * {@code @ManyToOne} associations. That is the whole point for a delete: a
 * foreign key would have to either cascade the audit row away with the account
 * (destroying the evidence that the deletion happened) or block the deletion
 * outright. The record has to outlive its subject.
 *
 * <p>The same reasoning applies to the pseudonymous references: they are copied
 * in at write time rather than derived on read, so the record stays meaningful
 * after the account it describes no longer exists.
 */
@Entity
@Table(name = "admin_audit_log")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    /** Null for an action taken by the system rather than a signed-in account. */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    /** Pseudonymous reference to the actor; see {@code UserPseudonym}. */
    @Column(name = "actor_ref", nullable = false, updatable = false)
    private String actorRef;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(name = "target_ref", nullable = false, updatable = false)
    private String targetRef;

    @Column(name = "client_ip", nullable = false, updatable = false)
    private String clientIp;

    /** Correlation id of the request, tying this row to the surrounding log lines. */
    @Column(name = "request_id", nullable = false, updatable = false)
    private String requestId;

    /**
     * What specifically changed, or the stated purpose of a read. Free text
     * because the useful content differs per action ({@code enabled=false},
     * {@code USER -> ADMIN}, a purpose string), and forcing it into a shared
     * column shape would flatten away the part worth reading.
     */
    @Column(nullable = false, updatable = false, length = 512)
    private String detail;

    protected AuditEvent() {
        // required by JPA
    }

    AuditEvent(
            AuditAction action,
            UUID actorId,
            String actorRef,
            UUID targetId,
            String targetRef,
            String clientIp,
            String requestId,
            String detail
    ) {
        this.occurredAt = Instant.now();
        this.action = action;
        this.actorId = actorId;
        this.actorRef = actorRef;
        this.targetId = targetId;
        this.targetRef = targetRef;
        this.clientIp = clientIp;
        this.requestId = requestId;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRef() {
        return actorRef;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDetail() {
        return detail;
    }
}

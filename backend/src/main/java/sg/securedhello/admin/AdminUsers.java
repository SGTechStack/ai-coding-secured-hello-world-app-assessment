package sg.securedhello.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.audit.AdminReadContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.user.UserAccountRepository;

/**
 * The admin read surface (PRD Story 8). The factor is enforced by the request matchers only; {@code @PreAuthorize}
 * repeats the role check and nothing else (ADR-026; ADR-043). Each read writes an audit row naming the acting admin.
 */
@Service
public class AdminUsers {

    private final UserAccountRepository accounts;
    private final AuditEmitter audit;

    AdminUsers(UserAccountRepository accounts, AuditEmitter audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    /** Every account, by username, for {@code actorId}; the row carries how many it returned. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AdminUserView> list(UUID actorId) {
        List<AdminUserView> users = accounts.findAll(Sort.by("username")).stream().map(AdminUserView::of).toList();
        audit.emit(AuditEvent.ADMIN_USERS_LISTED, AdminReadContext.listed(actorId, users.size()));
        return users;
    }

    /** One account, for {@code actorId}; empty when no account has {@code id}. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Optional<AdminUserView> find(UUID actorId, UUID id) {
        Optional<AdminUserView> user = accounts.findById(id).map(AdminUserView::of);
        user.ifPresent(found -> audit.emit(AuditEvent.ADMIN_USER_VIEWED, AdminReadContext.viewed(actorId, id)));
        return user;
    }
}

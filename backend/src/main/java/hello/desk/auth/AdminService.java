package hello.desk.auth;

import hello.desk.security.SessionService;
import hello.desk.user.PasswordResetTokenRepository;
import hello.desk.user.PublicUser;
import hello.desk.user.Role;
import hello.desk.user.UserAccount;
import hello.desk.user.UserAccountRepository;
import hello.desk.web.ApiException;
import hello.desk.web.ApiMessages;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository tokens;
    private final SessionService sessions;

    public AdminService(
            UserAccountRepository users,
            PasswordResetTokenRepository tokens,
            SessionService sessions) {
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<PublicUser> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(PublicUser::of).toList();
    }

    @Transactional(readOnly = true)
    public PublicUser current(String username) {
        return PublicUser.of(load(username));
    }

    @Transactional
    public PublicUser setEnabled(UUID targetId, boolean enabled, String actorUsername) {
        UserAccount actor = load(actorUsername);
        UserAccount target = load(targetId);
        rejectSelf(actor, target, enabled ? "enable" : "disable");
        target.setEnabled(enabled);
        users.save(target);
        if (!enabled) {
            sessions.invalidateUsername(target.getUsername());
        }
        log.info(
                "audit event={} actor={} target={}",
                enabled ? "account_enabled" : "account_disabled",
                actor.getUsername(),
                target.getUsername());
        return PublicUser.of(target);
    }

    @Transactional
    public PublicUser setRole(UUID targetId, Role role, String actorUsername) {
        UserAccount actor = load(actorUsername);
        UserAccount target = load(targetId);
        rejectSelf(actor, target, "role_change");
        target.setRole(role);
        users.save(target);
        sessions.invalidateUsername(target.getUsername());
        log.info(
                "audit event=role_change actor={} target={} role={}",
                actor.getUsername(),
                target.getUsername(),
                role);
        return PublicUser.of(target);
    }

    @Transactional
    public void delete(UUID targetId, String actorUsername) {
        UserAccount actor = load(actorUsername);
        UserAccount target = load(targetId);
        rejectSelf(actor, target, "delete");
        tokens.deleteAll(tokens.findByUser(target));
        sessions.invalidateUsername(target.getUsername());
        users.delete(target);
        log.info("audit event=account_deleted actor={} target={}", actor.getUsername(), target.getUsername());
    }

    private UserAccount load(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ApiMessages.UNAUTHORIZED));
    }

    private UserAccount load(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiMessages.USER_NOT_FOUND));
    }

    private void rejectSelf(UserAccount actor, UserAccount target, String action) {
        if (actor.getId().equals(target.getId())) {
            log.info("audit event=admin_self_action_rejected actor={} action={}", actor.getUsername(), action);
            throw new ApiException(HttpStatus.CONFLICT, ApiMessages.SELF_ACTION);
        }
    }
}

package sg.securedhello.profile;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.user.SignedInUser;

/**
 * {@code GET /api/profile}, the self-read, and {@code GET /api/hello}, the greeting (PRD Story 5). The self-read writes
 * row 35, a tier-2 keyed row (ADR-019; REJ-082); the greeting and the sign-in response's profile write none.
 */
@RestController
public class ProfileController {

    /** The greeting's body. */
    public record Greeting(String message) {
    }

    private final ProfileReader profiles;
    private final AuditEmitter audit;

    public ProfileController(ProfileReader profiles, AuditEmitter audit) {
        this.profiles = profiles;
        this.audit = audit;
    }

    @GetMapping("/api/profile")
    public Profile profile(Authentication authentication, @AuthenticationPrincipal SignedInUser user) {
        Profile profile = profiles.read(authentication);
        audit.emit(AuditEvent.PROFILE_READ, AccountContext.profileRead(user.id()));
        return profile;
    }

    @GetMapping("/api/hello")
    public Greeting hello(@AuthenticationPrincipal SignedInUser user) {
        return new Greeting("Hello, " + user.getUsername());
    }
}

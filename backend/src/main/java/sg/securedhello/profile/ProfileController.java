package sg.securedhello.profile;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.user.SignedInUser;

/** {@code GET /api/profile}, the self-read, and {@code GET /api/hello}, the greeting (PRD Story 5). */
@RestController
public class ProfileController {

    /** The greeting's body. */
    public record Greeting(String message) {
    }

    private final ProfileReader profiles;

    public ProfileController(ProfileReader profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/profile")
    public Profile profile(Authentication authentication) {
        return profiles.read(authentication);
    }

    @GetMapping("/api/hello")
    public Greeting hello(@AuthenticationPrincipal SignedInUser user) {
        return new Greeting("Hello, " + user.getUsername());
    }
}

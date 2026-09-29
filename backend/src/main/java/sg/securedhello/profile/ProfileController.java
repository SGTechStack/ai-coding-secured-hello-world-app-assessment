package sg.securedhello.profile;

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

    @GetMapping("/api/profile")
    public Profile profile(@AuthenticationPrincipal SignedInUser user) {
        return Profile.of(user);
    }

    @GetMapping("/api/hello")
    public Greeting hello(@AuthenticationPrincipal SignedInUser user) {
        return new Greeting("Hello, " + user.getUsername());
    }
}

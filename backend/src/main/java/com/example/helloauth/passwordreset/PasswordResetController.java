package com.example.helloauth.passwordreset;

import com.example.helloauth.auth.IpThrottleService;
import com.example.helloauth.auth.LoginThrottledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The password-reset endpoints. Both live under the permit-all
 * {@code /api/auth/**} prefix (anonymous callers are exactly who need them)
 * and both stay CSRF-protected like every other mutation.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    /**
     * The ratified enumeration-resistant wording (ticket 07) — returned
     * verbatim whether or not the email matches an account. The wording is a
     * security control: keep it in one place and never vary it.
     */
    static final String REQUEST_RESPONSE_MESSAGE =
        "If an account with that email exists, we've sent a reset link.";

    private final PasswordResetService passwordResetService;
    private final IpThrottleService ipThrottle;

    public PasswordResetController(PasswordResetService passwordResetService,
            IpThrottleService ipThrottle) {
        this.passwordResetService = passwordResetService;
        this.ipThrottle = ipThrottle;
    }

    @PostMapping("/request")
    public ResponseEntity<MessageResponse> request(
            @Valid @RequestBody PasswordResetRequest body,
            HttpServletRequest servletRequest) {
        // Anonymous-request throttle (security-review F-04): each request
        // can mint a token row — and under a real mailer, send mail — so the
        // endpoint draws from the per-IP anon budget. Check → record →
        // service, matching the codebase's gate-first convention; the 429
        // maps through ApiExceptionHandler like the login throttle.
        String remoteAddr = servletRequest.getRemoteAddr();
        if (remoteAddr != null) {
            if (ipThrottle.isAnonThrottled(remoteAddr)) {
                throw new LoginThrottledException(
                    "Too many requests. Try again later.");
            }
            ipThrottle.recordAnonRequest(remoteAddr);
        }
        passwordResetService.requestReset(body.email());
        return ResponseEntity.ok(new MessageResponse(REQUEST_RESPONSE_MESSAGE));
    }

    @PostMapping("/confirm")
    public ResponseEntity<MessageResponse> confirm(
            @Valid @RequestBody PasswordResetConfirm body) {
        passwordResetService.confirmReset(body.token(), body.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated."));
    }
}

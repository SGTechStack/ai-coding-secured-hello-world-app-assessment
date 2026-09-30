package com.assessment.hello.web;

import com.assessment.hello.domain.User;
import com.assessment.hello.dto.LoginRequest;
import com.assessment.hello.dto.MessageResponse;
import com.assessment.hello.dto.PasswordResetConfirmDto;
import com.assessment.hello.dto.PasswordResetRequestDto;
import com.assessment.hello.dto.RegisterRequest;
import com.assessment.hello.dto.UserView;
import com.assessment.hello.service.ApiException;
import com.assessment.hello.service.AuthService;
import com.assessment.hello.service.IpThrottleService;
import com.assessment.hello.service.SessionAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthService sessionAuthService;
    private final IpThrottleService ipThrottleService;

    public AuthController(AuthService authService,
                         SessionAuthService sessionAuthService,
                         IpThrottleService ipThrottleService) {
        this.authService = authService;
        this.sessionAuthService = sessionAuthService;
        this.ipThrottleService = ipThrottleService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserView> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        String ip = clientIp(httpRequest);
        if (ipThrottleService.isThrottled(ip)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts from this address. Try again later.");
        }

        try {
            User user = authService.authenticate(request.username(), request.password());
            ipThrottleService.reset(ip);
            sessionAuthService.establishSession(user, httpRequest, httpResponse);
            return ResponseEntity.ok(UserView.from(user));
        } catch (ApiException ex) {
            ipThrottleService.recordFailure(ip);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        sessionAuthService.endSession(request);
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestReset(@Valid @RequestBody PasswordResetRequestDto dto) {
        authService.requestPasswordReset(dto.email());
        // Always generic — never reveal whether the email is registered.
        return ResponseEntity.ok(new MessageResponse(
                "If that email is registered, a reset link has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResponse> confirmReset(@Valid @RequestBody PasswordResetConfirmDto dto,
                                                        HttpServletRequest request) {
        authService.confirmPasswordReset(dto.token(), dto.newPassword());
        // Invalidate the current session (all sessions are effectively invalidated by
        // the password change; here we clear the caller's context too).
        sessionAuthService.endSession(request);
        return ResponseEntity.ok(new MessageResponse("Password updated. Please log in again."));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package hello.desk.web;

import hello.desk.auth.AdminService;
import hello.desk.auth.AuthService;
import hello.desk.user.PublicUser;
import hello.desk.web.Payloads.CsrfBody;
import hello.desk.web.Payloads.LoginRequest;
import hello.desk.web.Payloads.MessageBody;
import hello.desk.web.Payloads.RegisterRequest;
import hello.desk.web.Payloads.ResetConfirmRequest;
import hello.desk.web.Payloads.ResetRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AdminService adminService;

    public AuthController(AuthService authService, AdminService adminService) {
        this.authService = authService;
        this.adminService = adminService;
    }

    @GetMapping("/csrf")
    public CsrfBody csrf(CsrfToken csrfToken) {
        return new CsrfBody(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<PublicUser> register(@Valid @RequestBody RegisterRequest request) {
        PublicUser created = authService.register(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public PublicUser login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return authService.login(
                request.username(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest,
                httpResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public PublicUser me(Authentication authentication) {
        return adminService.current(authentication.getName());
    }

    @PostMapping("/password-reset/request")
    public MessageBody requestReset(@Valid @RequestBody ResetRequest request) {
        authService.requestReset(request.email());
        return new MessageBody(ApiMessages.RESET_REQUEST);
    }

    @PostMapping("/password-reset/confirm")
    public MessageBody confirmReset(@Valid @RequestBody ResetConfirmRequest request) {
        authService.confirmReset(request.token(), request.password());
        return new MessageBody(ApiMessages.RESET_OK);
    }
}

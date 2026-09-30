package sg.example.helloauth.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF bootstrap for the SPA. The token lives in the server session (synchronizer pattern), so
 * the SPA can only obtain it here; it is never written to a cookie.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class CsrfController {

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    record CsrfResponse(String token, String headerName, String parameterName) {
    }
}

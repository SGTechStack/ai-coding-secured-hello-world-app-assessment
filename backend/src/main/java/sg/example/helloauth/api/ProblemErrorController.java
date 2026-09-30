package sg.example.helloauth.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's default {@code /error} body so that errors raised outside Spring MVC (by the
 * servlet container or a filter) are Problem Details too, and never carry exception details.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    // Every method: an error dispatch keeps the method of the request that failed.
    @RequestMapping("${spring.web.error.path:/error}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                && HttpStatus.resolve(code) != null ? HttpStatus.valueOf(code) : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(ProblemDetails.forStatus(status, status.getReasonPhrase()));
    }
}

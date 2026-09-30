package sg.securedhello.error;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Envelope producer for the {@code /error} dispatch (ADR-031, T-AUTH-001): whatever escapes the advice and the
 * security handlers and reaches the container's error page is rendered as the shared envelope. Declaring an
 * {@link ErrorController} makes Boot's {@code BasicErrorController} back off, so its
 * {@code timestamp/error/message/path} body is never produced.
 *
 * <p>An escaped exception is always {@link ErrorCode#INTERNAL_ERROR}; otherwise the code comes from the status the
 * container recorded. The exception is logged once, with its code's classification ({@link ErrorLog}), never written.
 */
@Controller
public class ProblemErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

    private final ProblemDetailWriter writer;

    public ProblemErrorController(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @RequestMapping("${server.error.path:/error}")
    void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorCode code;
        if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable escaped) {
            ErrorLog.unhandled(log, "Exception reached the error dispatch", escaped);
            code = ErrorCode.INTERNAL_ERROR;
        } else {
            code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status
                    ? ErrorCode.forStatus(status)
                    : ErrorCode.INTERNAL_ERROR;
        }
        writer.write(request, response, code);
    }
}

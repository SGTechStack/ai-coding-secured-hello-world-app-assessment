package sg.example.helloauth.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Rejects a request body that is larger than the API accepts, or that doesn't declare its size
 * (a chunked body could be any size), before anything reads it. Both get a
 * {@code validation failed} Problem Details body. The SPA always sends a {@code Content-Length}.
 */
public final class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final HandlerExceptionResolver errors;

    public RequestBodyLimitFilter(DataSize max, HandlerExceptionResolver errors) {
        this.maxBytes = max.toBytes();
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            reject(request, response, "The request body must declare its length.");
        } else if (request.getContentLengthLong() > maxBytes) {
            reject(request, response, "The request body is too large.");
        } else {
            chain.doFilter(request, response);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String detail) {
        errors.resolveException(request, response, null,
                new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, detail));
    }
}

package sg.securedhello.security.ratelimit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * The request body cap (Std §5:494; T-RL-019), after the per-source filter, so an oversized request still spends its
 * source token (T-RL-015), and before the login converter reads the body (T-RL-012). The cap is counted in bytes as
 * the body is read, not taken from {@code Content-Length}: a declared length over the cap is refused at once, and a
 * chunked or understated body fails the read at byte cap + 1 (T-RL-013; T-RL-014). Either way the answer is 400
 * {@code VALIDATION_FAILED}: from here, or from whoever was reading, which treats the failed read as a malformed body.
 *
 * <p>The cap applies to the body read through {@code getInputStream} or {@code getReader}. Form parameters are
 * parsed by the container from its own stream, under its own {@code maxPostSize}; no route here takes a form.
 */
public final class RequestBodyCapFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ProblemDetailWriter writer;

    public RequestBodyCapFilter(long maxBytes, ProblemDetailWriter writer) {
        this.maxBytes = maxBytes;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            writer.write(request, response, ErrorCode.VALIDATION_FAILED);
            return;
        }
        chain.doFilter(new CappedRequest(request, maxBytes), response);
    }

    /** Thrown by a read that would pass the cap. */
    static final class BodyTooLargeException extends IOException {

        BodyTooLargeException(long maxBytes) {
            super("The request body is larger than " + maxBytes + " bytes");
        }
    }

    /** The request, whose body can be read only up to the cap. */
    private static final class CappedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream body;

        CappedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (body == null) {
                body = new CappedInputStream(super.getInputStream(), maxBytes);
            }
            return body;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    /** Counts the bytes read, and fails the read that goes past the cap. */
    private static final class CappedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        CappedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                counted(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                counted(count);
            }
            return count;
        }

        private void counted(int bytes) throws BodyTooLargeException {
            read += bytes;
            if (read > maxBytes) {
                throw new BodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}

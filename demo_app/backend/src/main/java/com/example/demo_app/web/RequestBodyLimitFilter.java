package com.example.demo_app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps request bodies at {@link #MAX_BODY_BYTES}. A declared {@code Content-Length} over the cap
 * is answered {@code 413} JSON before anything else reads the body. A body of undeclared length
 * (chunked) is counted as it is read and fails with {@link BodyTooLargeException} once it passes
 * the cap, which {@code ApiExceptionHandler} also answers {@code 413}.
 */
public final class RequestBodyLimitFilter extends OncePerRequestFilter {

  public static final int MAX_BODY_BYTES = 16 * 1024;

  private final ObjectMapper objectMapper;

  public RequestBodyLimitFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  static ApiError tooLarge(String path) {
    return ApiError.of(
        HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large", path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      ApiError body = tooLarge(request.getRequestURI());
      response.setStatus(body.status());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), body);
      return;
    }
    chain.doFilter(new BoundedBodyRequest(request), response);
  }

  /** Thrown while reading a body of undeclared length once it passes the cap. */
  static final class BodyTooLargeException extends IOException {
    BodyTooLargeException() {
      super("Request body exceeds " + MAX_BODY_BYTES + " bytes");
    }
  }

  private static final class BoundedBodyRequest extends HttpServletRequestWrapper {

    private ServletInputStream stream;

    BoundedBodyRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new BoundedInputStream(super.getInputStream());
      }
      return stream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }

  private static final class BoundedInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private long count;

    BoundedInputStream(ServletInputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      int b = delegate.read();
      if (b != -1) {
        count(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = delegate.read(buffer, offset, length);
      if (read > 0) {
        count(read);
      }
      return read;
    }

    private void count(int read) throws BodyTooLargeException {
      count += read;
      if (count > MAX_BODY_BYTES) {
        throw new BodyTooLargeException();
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

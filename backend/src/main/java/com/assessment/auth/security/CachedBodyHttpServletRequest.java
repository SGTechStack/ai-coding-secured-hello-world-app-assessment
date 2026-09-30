package com.assessment.auth.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body can be read more than once.
 *
 * <p>Needed because the per-account rate limiter keys on the <em>username</em>, which lives in the
 * login body, and it runs <strong>before</strong> authentication (spec.md S4, filter 2). Without
 * this the rate limiter would consume the stream and {@link JsonAuthenticationFilter} would receive
 * an empty body.
 *
 * <p>Spring's {@code ContentCachingRequestWrapper} does not solve this: it captures bytes as they
 * are read for later inspection, it does not make the stream re-readable.
 *
 * <p>Applied only to the endpoints the rate limiter inspects, so no other request pays the cost of
 * buffering its body.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
    super(request);
    this.body = request.getInputStream().readAllBytes();
  }

  public byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream buffer = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return buffer.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Asynchronous reads are not used on this path.");
      }

      @Override
      public int read() {
        return buffer.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}

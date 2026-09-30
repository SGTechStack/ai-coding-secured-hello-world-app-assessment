package local.builderday.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers every request to a frontend path (ADR 0007): an HTML GET gets the SPA document with a unique strict-CSP
 * nonce per response, any other GET a 404 and any other method a 405. Runs after the Spring Security chain, so the CSRF
 * check comes first.
 */
@Component
public class SpaDocumentRequestFilter extends OncePerRequestFilter {
  private static final String NONCE_PLACEHOLDER = "__CSP_NONCE__";
  private static final SecureRandom RANDOM = new SecureRandom();
  private final Resource templateResource;

  SpaDocumentRequestFilter(@Value("classpath:static/index.html") Resource templateResource) {
    this.templateResource = templateResource;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!isFrontendPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    if (!"GET".equals(request.getMethod())) {
      response.setHeader(HttpHeaders.ALLOW, "GET");
      ProblemDetails.write(request, response, ApiError.METHOD_NOT_ALLOWED);
      return;
    }
    final boolean acceptsHtml;
    try {
      acceptsHtml = acceptsHtml(request);
    } catch (InvalidMediaTypeException exception) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if (!acceptsHtml) {
      ProblemDetails.write(request, response, ApiError.RESOURCE_NOT_FOUND);
      return;
    }

    byte[] nonceBytes = new byte[16];
    RANDOM.nextBytes(nonceBytes);
    String nonce = Base64.getEncoder().encodeToString(nonceBytes);
    String document = templateResource.getContentAsString(StandardCharsets.UTF_8).replace(NONCE_PLACEHOLDER, nonce);
    String policy = "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; "
        + "script-src 'self' 'nonce-" + nonce + "'; style-src 'self' 'nonce-" + nonce
        + "'; style-src-attr 'none'; form-action 'self'";

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8).toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("Content-Security-Policy", policy);
    response.getWriter().write(document);
  }

  /**
   * Every path the server does not own: not {@code /csrf}, {@code /api/**}, {@code /actuator/**}, {@code /assets/**},
   * {@code /images/**}, {@code /favicon.ico}, nor the servlet container's {@code /error} dispatch.
   */
  public static boolean isFrontendPath(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !(ProblemDetails.isApiRequest(request) || under(path, "/actuator") || under(path, "/assets")
        || under(path, "/images") || path.equals("/favicon.ico") || path.equals("/error"));
  }

  private static boolean under(String path, String folder) {
    return path.equals(folder) || path.startsWith(folder + "/");
  }

  /** @throws InvalidMediaTypeException if the Accept header is malformed */
  private static boolean acceptsHtml(HttpServletRequest request) {
    String accept = request.getHeader(HttpHeaders.ACCEPT);
    if (accept == null || accept.isBlank()) {
      return true;
    }
    return MediaType.parseMediaTypes(accept).stream()
        .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_HTML));
  }
}

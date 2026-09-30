package local.builderday.auth.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.auth.core.service.Authentications;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Answers a denied request of a caller Spring Security did not send to the authentication entry point, i.e. one with a
 * Session (ADR 0007). A CSRF rejection wins. Otherwise the controller mappings decide: no endpoint on the path is 404,
 * the path without that method is 405, and an existing endpoint the caller's role is not granted is 403.
 */
class DeniedRequestHandler implements AccessDeniedHandler {
  private final UserProfileService userProfileService;
  private final RequestMappingHandlerMapping requestMappingHandlerMapping;

  DeniedRequestHandler(UserProfileService userProfileService,
      RequestMappingHandlerMapping requestMappingHandlerMapping) {
    this.userProfileService = userProfileService;
    this.requestMappingHandlerMapping = requestMappingHandlerMapping;
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    // CsrfFilter reports missing and invalid tokens here too; the SPA treats that code after login as Session expired,
    // so it must never be confused with an authorization denial.
    if (exception instanceof CsrfException) {
      audit(request, "csrf-check", "csrf_rejected");
      ProblemDetails.write(request, response, ApiError.CSRF_TOKEN_REJECTED);
    } else {
      var denial = denialFor(request, response);
      // Structured Logging App Standard 3.4: every authorisation failure is audited, never naming roles or rules.
      // 404 and 405 are not denials: the caller asked for something that does not exist.
      if (denial == ApiError.ACCESS_DENIED) audit(request, "authorization", "access_denied");
      ProblemDetails.write(request, response, denial);
    }
  }

  private void audit(HttpServletRequest request, String action, String reason) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    SecurityAudit.record(request, new SecurityAudit.Event(action, "web", "access", SecurityAudit.Outcome.FAILURE,
        reason, Authentications.isAuthenticatedUser(authentication)
            ? userProfileService.findIdForAudit(authentication.getName()) : null));
  }

  private ApiError denialFor(HttpServletRequest request, HttpServletResponse response) {
    // Only annotated controllers count: the static resource mapping matches every path.
    var probe = new AttributeIsolatingRequest(request);
    try {
      ServletRequestPathUtils.parseAndCache(probe);
      return requestMappingHandlerMapping.getHandler(probe) == null
          ? ApiError.RESOURCE_NOT_FOUND
          : ApiError.ACCESS_DENIED;
    } catch (HttpRequestMethodNotSupportedException wrongMethod) {
      var supported = wrongMethod.getSupportedMethods();
      if (supported != null) response.setHeader(HttpHeaders.ALLOW, String.join(", ", supported));
      return ApiError.METHOD_NOT_ALLOWED;
    } catch (Exception otherMismatch) {
      // The path and method match but, e.g., the content type does not: the endpoint exists, so stay denied.
      return ApiError.ACCESS_DENIED;
    }
  }

  /**
   * Keeps the attributes a handler lookup records (matched handler, route pattern, path variables) off the real
   * request, so a request the security chain rejected never looks as if a controller handled it.
   */
  private static final class AttributeIsolatingRequest extends HttpServletRequestWrapper {
    private final Map<String, Object> attributes = new HashMap<>();

    AttributeIsolatingRequest(HttpServletRequest request) { super(request); }

    @Override public Object getAttribute(String name) {
      return attributes.containsKey(name) ? attributes.get(name) : super.getAttribute(name);
    }

    @Override public void setAttribute(String name, Object value) { attributes.put(name, value); }

    @Override public void removeAttribute(String name) { attributes.put(name, null); }
  }
}

package local.builderday.account.registration.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.FieldErrorResponse;
import local.builderday.common.exception.ProblemDetails;
import local.builderday.account.registration.controller.dto.RegistrationResponse;
import local.builderday.account.registration.model.RegistrationViolation;
import local.builderday.account.registration.service.RegistrationResult;
import local.builderday.account.registration.service.RegistrationService;
import local.builderday.account.registration.service.dto.RegistrationInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Visitor API endpoint that creates an account and leaves authentication to the normal login flow. Owns only HTTP
 * concerns: bounded body reading, response mapping and auditing. Lockout and validation policy live in the service.
 *
 * <p>The body is read by hand rather than bound to a {@code @Valid} record because the contract needs
 * {@code REQUEST_TOO_LARGE}, {@code FIELD_NOT_ALLOWED} and every field violation reported together as stable codes.
 */
@RestController
public class RegistrationController {
  static final int MAX_BODY_BYTES = 4 * 1024;
  static final String CREATED_MESSAGE = "Account created. You can now log in.";
  private static final Set<String> ALLOWED_FIELDS = Set.of("username", "email", "password");

  private final RegistrationService registrationService;
  private final ObjectMapper json;

  RegistrationController(RegistrationService registrationService, ObjectMapper json) {
    this.registrationService = registrationService;
    this.json = json;
  }

  @PostMapping(path = "/api/auth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> register(HttpServletRequest request) throws IOException {
    // Already the client IP: Tomcat's RemoteIpValve (server.tomcat.remoteip).
    try {
      return switch (registrationService.register(request.getRemoteAddr(), read(request))) {
        case RegistrationResult.Created(var userId) -> {
          audit(request, SecurityAudit.Outcome.SUCCESS, "success", userId, List.of());
          yield ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationResponse(CREATED_MESSAGE));
        }
        case RegistrationResult.Locked() -> {
          audit(request, SecurityAudit.Outcome.FAILURE, "rate_limited", null, List.of());
          yield ProblemDetails.response(ApiError.REGISTRATION_UNAVAILABLE);
        }
        case RegistrationResult.Rejected(var violations) -> {
          audit(request, SecurityAudit.Outcome.FAILURE, "rejected", null,
              violations.stream().map(RegistrationViolation::code).distinct().toList());
          yield ProblemDetails.rejected(ApiError.REGISTRATION_REJECTED,
              violations.stream()
                  .map(violation -> new FieldErrorResponse(violation.field(), violation.code()))
                  .toList());
        }
      };
    } catch (RuntimeException | IOException failure) {
      audit(request, SecurityAudit.Outcome.ERROR, "system_error", null, List.of());
      throw failure;
    }
  }

  /**
   * @param userId the created account, only on success
   * @param errorCodes stable rejection codes, only for {@code rejected}
   */
  private static void audit(HttpServletRequest request, SecurityAudit.Outcome outcome, String reason, UUID userId,
      List<String> errorCodes) {
    SecurityAudit.record(request, new SecurityAudit.Event("user-registration", "iam", "creation", outcome, reason,
        userId, errorCodes.isEmpty() ? Map.of() : Map.of("error.code", List.copyOf(errorCodes))));
  }

  /** Reads at most 4 KB and reduces the body to its three allowed string fields plus any structural violations. */
  private RegistrationInput read(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) return unreadable(RegistrationViolation.REQUEST_TOO_LARGE);
    byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) return unreadable(RegistrationViolation.REQUEST_TOO_LARGE);
    JsonNode root;
    try {
      root = json.readTree(body);
    } catch (JacksonException malformed) {
      return RegistrationInput.unreadable(List.of());
    }
    if (root == null || !root.isObject()) return RegistrationInput.unreadable(List.of());
    var violations = new ArrayList<RegistrationViolation>();
    if (root.properties().stream().anyMatch(property -> !ALLOWED_FIELDS.contains(property.getKey()))) {
      violations.add(RegistrationViolation.of(RegistrationViolation.FIELD_NOT_ALLOWED));
    }
    return new RegistrationInput(text(root, "username"), text(root, "email"), text(root, "password"), violations, true);
  }

  private static RegistrationInput unreadable(String code) {
    return RegistrationInput.unreadable(List.of(RegistrationViolation.of(code)));
  }

  /** A non-string value is treated as absent, which the service reports as FIELD_REQUIRED. */
  private static String text(JsonNode root, String field) {
    JsonNode value = root.get(field);
    return value != null && value.isString() ? value.stringValue() : null;
  }
}

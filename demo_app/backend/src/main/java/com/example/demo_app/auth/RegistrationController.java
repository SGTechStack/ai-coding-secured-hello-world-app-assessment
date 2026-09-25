package com.example.demo_app.auth;

import com.example.demo_app.audit.Actor;
import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.IpThrottle;
import com.example.demo_app.user.AccountRegistration;
import com.example.demo_app.user.Role;
import com.example.demo_app.user.UserAccount;
import com.example.demo_app.web.ApiException;
import com.example.demo_app.web.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-registration: anonymous and CSRF-protected like every state-changing endpoint.
 *
 * <p>Every request counts against the per-IP registration throttle before its body is looked at,
 * whatever the outcome, so neither mass account creation nor probing for taken usernames and
 * emails (the {@code 409} reveals them) can go faster than the limit. The {@link BindingResult}
 * parameter lets the throttle run before validation errors are reported.
 *
 * <p>Success is {@code 201} with the new profile and <strong>no session</strong>: the user logs in
 * next, through the normal login with its own throttle and audit.
 */
@RestController
@RequestMapping("/api/v1/auth")
class RegistrationController {

  private final AccountRegistration registration;
  private final AuditLog auditLog;
  private final IpThrottle registrationThrottle;

  RegistrationController(
      AccountRegistration registration,
      AuditLog auditLog,
      @Qualifier("registrationThrottle") IpThrottle registrationThrottle) {
    this.registration = registration;
    this.auditLog = auditLog;
    this.registrationThrottle = registrationThrottle;
  }

  @PostMapping(
      value = "/register",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<UserProfile> register(
      @Valid @RequestBody RegisterRequest body,
      BindingResult validation,
      HttpServletRequest request) {
    try {
      registrationThrottle.acquire(request);
    } catch (TooManyRequestsException e) {
      auditLog.record(
          AuditEvent.REQUEST_THROTTLED, Actor.anonymous(request), Map.of("endpoint", "register"));
      throw e;
    }
    if (validation.hasErrors()) {
      throw ApiException.validationFailed(validation);
    }

    UserAccount account = registration.register(body.toNewAccount(), Role.USER);
    auditLog.record(AuditEvent.USER_REGISTERED, Actor.of(account.getUsername(), request));
    return ResponseEntity.status(HttpStatus.CREATED).body(UserProfile.of(account));
  }
}

package local.builderday.auth.csrf.controller.dto;

/** Session-backed CSRF material returned to the browser bootstrap. */
public record CsrfResponse(String token, String headerName, String parameterName) {}

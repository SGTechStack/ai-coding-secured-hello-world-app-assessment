package local.builderday.auth.login.controller.dto;

import local.builderday.account.core.model.UserProfile;

/** Session profile created by a successful login. The new session's CSRF token is fetched from {@code GET /csrf}. */
public record LoginResponse(UserProfile profile) {}

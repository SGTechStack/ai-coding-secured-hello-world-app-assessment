package local.builderday.common.web;

import java.util.UUID;

/**
 * The contract every authenticated principal implements, so the request log and MDC can attribute a request to its
 * User without a lookup and without naming them. A new authentication path must produce such a principal too.
 */
public interface AuthenticatedUser {
  /** @return the User's id, never the username */
  UUID userId();
}

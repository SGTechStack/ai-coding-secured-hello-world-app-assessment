package org.eds.demo.user.application;

import java.time.Instant;
import org.eds.demo.user.domain.Role;

/** What an admin sees of an Account in the list; deliberately excludes all credential state. */
public record AccountSummary(String username, Role role, boolean enabled, Instant createdAt) {}

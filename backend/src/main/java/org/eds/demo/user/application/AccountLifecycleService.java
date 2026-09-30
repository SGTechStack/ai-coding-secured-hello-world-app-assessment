package org.eds.demo.user.application;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.common.exception.ConflictException;
import org.eds.demo.common.exception.NotFoundException;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin use cases that suspend, re-Role or remove an existing Account. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

  private static final String ACCOUNT_RESOURCE = "Account";
  private static final String EVENT_KEY = "event";
  private static final String ACTOR_KEY = "actor";
  private static final String TARGET_KEY = "target";

  /** Audit event names, stable so log queries can filter on them. */
  private static final String EVENT_ACCOUNT_ENABLED = "account_enabled";

  private static final String EVENT_ACCOUNT_DISABLED = "account_disabled";
  private static final String EVENT_ACCOUNT_ROLE_CHANGED = "account_role_changed";
  private static final String EVENT_ACCOUNT_DELETED = "account_deleted";

  private final AppUserRepository appUserRepository;
  private final AccountSessions accountSessions;

  /** Disabling ends the Account's sessions at once. */
  @Transactional
  public void setEnabled(String actor, UUID id, boolean enabled) {
    var target = find(id);
    if (enabled) {
      target.enable();
      audit(EVENT_ACCOUNT_ENABLED, "Account enabled", actor, target);
      return;
    }
    refuseSelf(actor, target, "disable");
    target.disable();
    accountSessions.endAllSessions(target.getUsername());
    audit(EVENT_ACCOUNT_DISABLED, "Account disabled", actor, target);
  }

  /**
   * Gives the Account a single new Role and ends its sessions so no session keeps the old
   * authorities. An admin may not demote their own Account.
   */
  @Transactional
  public void changeRole(String actor, UUID id, Role role) {
    var target = find(id);
    if (target.getRoles().equals(Set.of(role))) {
      // Nothing changes, so no session holds stale authorities; ending the actor's own would
      // sign them out for no reason.
      return;
    }
    if (role != Role.ADMIN) {
      refuseSelf(actor, target, "demote");
    }
    var previous = target.getRoles().stream().max(Comparator.naturalOrder()).orElseThrow();
    target.replaceRolesWith(role);
    accountSessions.endAllSessions(target.getUsername());
    log.atInfo()
        .addKeyValue(EVENT_KEY, EVENT_ACCOUNT_ROLE_CHANGED)
        .addKeyValue(ACTOR_KEY, actor)
        .addKeyValue(TARGET_KEY, target.getUsername())
        .addKeyValue("from", previous)
        .addKeyValue("to", role)
        .log(
            "Account role changed: actor={}, target={}, from={}, to={}",
            actor,
            target.getUsername(),
            previous,
            role);
  }

  /** Removes the Account with its Roles and ends its sessions at once. */
  @Transactional
  public void delete(String actor, UUID id) {
    var target = find(id);
    refuseSelf(actor, target, "delete");
    appUserRepository.delete(target);
    accountSessions.endAllSessions(target.getUsername());
    audit(EVENT_ACCOUNT_DELETED, "Account deleted", actor, target);
  }

  private static void audit(String event, String message, String actor, AppUser target) {
    log.atInfo()
        .addKeyValue(EVENT_KEY, event)
        .addKeyValue(ACTOR_KEY, actor)
        .addKeyValue(TARGET_KEY, target.getUsername())
        .log(message + ": actor={}, target={}", actor, target.getUsername());
  }

  /** An admin acting on their own Account could lock themselves out or orphan the admin role. */
  private static void refuseSelf(String actor, AppUser target, String action) {
    if (target.getUsername().equals(actor)) {
      throw new ConflictException("You cannot " + action + " your own account");
    }
  }

  private AppUser find(UUID id) {
    return appUserRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException(ACCOUNT_RESOURCE, id));
  }
}

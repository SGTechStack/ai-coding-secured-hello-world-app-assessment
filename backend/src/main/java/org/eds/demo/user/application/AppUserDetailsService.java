package org.eds.demo.user.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.config.LocalUsersProperties;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads or provisions an {@link AppUser} and returns {@link AppUserDetails} as the principal. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

  private final AppUserRepository appUserRepository;
  private final Environment environment;
  private final Optional<LocalUsersProperties> localUsersProperties;

  @Override
  @Transactional
  public AppUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Optional<LocalUsersProperties.UserEntry> localEntry = findLocalUser(username);
    Set<Role> roles = localEntry.map(entry -> entry.roles()).orElse(EnumSet.of(Role.USER));
    String configuredEmail = localEntry.map(entry -> entry.email()).orElse(null);

    AppUser appUser =
        appUserRepository
            .findByUsername(username)
            .map(
                existing -> {
                  if (localEntry.isPresent()
                      && !java.util.Objects.equals(configuredEmail, existing.getEmail())) {
                    existing.updateEmail(configuredEmail);
                    return appUserRepository.save(existing);
                  }
                  return existing;
                })
            .orElseGet(
                () -> {
                  var newUser = AppUser.create(username, roles);
                  if (configuredEmail != null && !configuredEmail.isBlank()) {
                    newUser.updateEmail(configuredEmail);
                  }
                  var saved = appUserRepository.save(newUser);
                  log.info(
                      "Provisioned new AppUser: username={}, id={}, email={}",
                      username,
                      saved.getId(),
                      saved.getEmail());
                  return saved;
                });

    List<SimpleGrantedAuthority> authorities =
        appUser.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .toList();

    // Non-local: auth handled upstream (SSO/IdP), password never checked.
    // Local: fixed password "password" for all dev accounts.
    String password = environment.matchesProfiles("local") ? "{noop}password" : "";

    return new AppUserDetails(
        appUser.getId(), appUser.getUsername(), appUser.getUsername(), password, authorities);
  }

  private Optional<LocalUsersProperties.UserEntry> findLocalUser(String username) {
    return localUsersProperties
        .map(usersProp -> usersProp.users())
        .flatMap(users -> users.stream().filter(u -> u.username().equals(username)).findFirst());
  }
}

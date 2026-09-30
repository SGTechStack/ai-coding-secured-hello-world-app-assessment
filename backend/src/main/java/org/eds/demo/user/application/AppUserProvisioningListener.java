package org.eds.demo.user.application;

import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AppUserProvisioningListener {

  private final AppUserRepository appUserRepository;

  public AppUserProvisioningListener(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @EventListener
  @Transactional
  public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
    Object principal = event.getAuthentication().getPrincipal();
    if (!(principal instanceof UserDetails userDetails)) {
      return;
    }

    String username = userDetails.getUsername();
    AppUser appUser =
        appUserRepository
            .findByUsername(username)
            .orElseGet(
                () -> {
                  var newUser = AppUser.create(username, EnumSet.of(Role.USER));
                  var savedUser = appUserRepository.save(newUser);
                  log.info(
                      "Provisioned new AppUser: username={}, id={}", username, savedUser.getId());
                  return savedUser;
                });

    var authorities =
        appUser.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .toList();

    var appUserDetails =
        new AppUserDetails(
            appUser.getId(),
            appUser.getUsername(),
            appUser.getUsername(),
            userDetails.getPassword() != null ? userDetails.getPassword() : "",
            authorities);

    var newAuth =
        new UsernamePasswordAuthenticationToken(
            appUserDetails, event.getAuthentication().getCredentials(), authorities);
    SecurityContextHolder.getContext().setAuthentication(newAuth);
  }
}

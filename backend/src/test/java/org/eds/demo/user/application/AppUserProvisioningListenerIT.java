package org.eds.demo.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AppUserProvisioningListenerIT {

  @Autowired private AppUserProvisioningListener listener;

  @Autowired private AppUserRepository appUserRepository;

  @Test
  void createsAppUserOnFirstLogin() {
    var userDetails =
        User.withUsername("newuser").password("pass").authorities("ROLE_USER").build();
    var auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    var event = new AuthenticationSuccessEvent(auth);

    listener.onAuthenticationSuccess(event);

    var appUser = appUserRepository.findByUsername("newuser");
    assertThat(appUser).isPresent();
    assertThat(appUser.get().getRoles()).containsExactly(Role.USER);
  }

  @Test
  void doesNotDuplicateExistingUser() {
    var existing =
        AppUser.create("existinguser", java.util.EnumSet.of(Role.USER, Role.USER_MANAGER));
    appUserRepository.save(existing);

    var userDetails =
        User.withUsername("existinguser").password("pass").authorities("ROLE_USER").build();
    var auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    var event = new AuthenticationSuccessEvent(auth);

    listener.onAuthenticationSuccess(event);

    List<AppUser> all = appUserRepository.findAll();
    assertThat(all.stream().filter(u -> u.getUsername().equals("existinguser")).count())
        .isEqualTo(1);
  }

  @Test
  void ignoresNonUserDetailsPrincipal() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "string-principal", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    var event = new AuthenticationSuccessEvent(auth);

    listener.onAuthenticationSuccess(event);

    assertThat(appUserRepository.findAll()).isEmpty();
  }

  @Test
  void replacesSecurityContextWithAppUserDetails() {
    var userDetails =
        User.withUsername("contextuser").password("pass").authorities("ROLE_USER").build();
    var auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    var event = new AuthenticationSuccessEvent(auth);

    listener.onAuthenticationSuccess(event);

    var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    assertThat(principal).isInstanceOf(AppUserDetails.class);
    var appUserDetails = (AppUserDetails) principal;
    assertThat(appUserDetails.getUsername()).isEqualTo("contextuser");
    assertThat(appUserDetails.getUserId()).isNotNull();
    assertThat(appUserDetails.getDisplayName()).isEqualTo("contextuser");
  }
}

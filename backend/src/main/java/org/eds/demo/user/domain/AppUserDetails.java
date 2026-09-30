package org.eds.demo.user.domain;

import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

@Getter
public class AppUserDetails extends User {

  private final UserId userId;
  private final String displayName;

  public AppUserDetails(
      UserId userId,
      String username,
      String displayName,
      String password,
      boolean enabled,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, enabled, true, true, true, authorities);
    this.userId = userId;
    this.displayName = displayName;
  }
}

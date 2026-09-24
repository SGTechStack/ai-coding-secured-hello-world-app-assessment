package com.example.demo_app.auth;

import com.example.demo_app.user.AccountUserDetails;
import com.example.demo_app.user.Role;
import com.example.demo_app.user.UserAccount;

/** Public view of the signed-in user returned by the auth API. */
public record UserProfile(String username, String firstName, Role role) {

  static UserProfile of(AccountUserDetails user) {
    return new UserProfile(user.getUsername(), user.getFirstName(), user.getRole());
  }

  static UserProfile of(UserAccount account) {
    return new UserProfile(account.getUsername(), account.getFirstName(), account.getRole());
  }
}

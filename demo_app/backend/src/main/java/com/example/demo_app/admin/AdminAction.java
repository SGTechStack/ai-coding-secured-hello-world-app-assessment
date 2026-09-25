package com.example.demo_app.admin;

import java.util.Locale;

/** The account actions an admin can take, as the {@code action} field of a refused one's audit. */
enum AdminAction {
  STATUS,
  ROLE,
  DELETE;

  /** The lower-case name the audit line carries ({@code status}, {@code role}, {@code delete}). */
  String auditName() {
    return name().toLowerCase(Locale.ROOT);
  }
}

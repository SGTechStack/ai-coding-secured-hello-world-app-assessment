package com.example.demo_app.admin;

import com.example.demo_app.user.UserAccountRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management for admins. Every method requires {@code ROLE_ADMIN} itself, on top of the
 * filter chain's rule for {@code /api/v1/admin/**}, so a future caller outside that path (or a
 * mistake in the path rule) still can't reach it without the role.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
class AdminUserService {

  private final UserAccountRepository accounts;

  AdminUserService(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  /** Every account, oldest first (ties by id, so the order is stable). */
  @Transactional(readOnly = true)
  List<AdminUserView> listUsers() {
    return accounts.findAllByOrderByCreatedAtAscIdAsc().stream().map(AdminUserView::of).toList();
  }
}

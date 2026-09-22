package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every endpoint under {@code /api/admin/**} requires {@code ROLE_ADMIN},
 * enforced in {@code SecurityConfig} — never re-checked here, and never
 * trusted from anything client-supplied.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
    }
}

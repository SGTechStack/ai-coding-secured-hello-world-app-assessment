package com.assessment.securedhelloworld.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists login attempt counts to the database so the total is correct
 * across every backend instance, not just the one that handled a given
 * request (see {@link LoginCount}).
 */
@Service
public class LoginCountService {

    private final LoginCountRepository loginCountRepository;

    public LoginCountService(LoginCountRepository loginCountRepository) {
        this.loginCountRepository = loginCountRepository;
    }

    @Transactional
    public void increment(LoginOutcome outcome) {
        int rowsUpdated = loginCountRepository.increment(outcome);
        if (rowsUpdated == 0) {
            // First attempt of this outcome anywhere: seed the row, then
            // retry the atomic increment so a concurrent seed from
            // another instance can't silently lose this attempt (the
            // unique @Id constraint on `outcome` means at most one insert
            // wins; whichever loses falls through to the increment below).
            try {
                loginCountRepository.save(new LoginCount(outcome));
            } catch (org.springframework.dao.DataIntegrityViolationException alreadySeededConcurrently) {
                // Another instance/thread inserted the row first - fine,
                // fall through to the increment which will now succeed.
            }
            loginCountRepository.increment(outcome);
        }
    }

    public long countFor(LoginOutcome outcome) {
        return loginCountRepository.findById(outcome)
                .map(LoginCount::getCount)
                .orElse(0L);
    }
}

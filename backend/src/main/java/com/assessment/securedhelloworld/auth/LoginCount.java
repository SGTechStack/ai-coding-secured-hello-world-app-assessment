package com.assessment.securedhelloworld.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A running total of login attempts by {@link LoginOutcome}, persisted so
 * the count is correct in aggregate across every backend instance behind
 * a load balancer — an in-process {@code Counter} (Micrometer) only
 * tracks the instance that handled the request, not the fleet-wide total.
 *
 * <p>One row per {@link LoginOutcome}; {@code count} is incremented via a
 * single atomic {@code UPDATE ... SET count = count + 1} (see
 * {@link LoginCountRepository#increment}) so concurrent requests across
 * multiple instances never race on a read-then-write.
 */
@Entity
@Table(name = "login_counts")
public class LoginCount {

    @Id
    @Enumerated(EnumType.STRING)
    private LoginOutcome outcome;

    @Column(nullable = false)
    private long count = 0L;

    protected LoginCount() {
        // JPA
    }

    public LoginCount(LoginOutcome outcome) {
        this.outcome = outcome;
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public long getCount() {
        return count;
    }
}

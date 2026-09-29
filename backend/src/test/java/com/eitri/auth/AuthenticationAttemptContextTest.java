package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.audit.AuditAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationAttemptContextTest {

    private static final AuditAccount ACCOUNT =
            new AuditAccount(UUID.fromString("11111111-1111-1111-1111-111111111111"), "johndoe");

    @Test
    void carriesLoadedIdentityOnlyForAnActiveAttemptAndClearsIt() {
        AuthenticationAttemptContext context = new AuthenticationAttemptContext();
        context.accountLoaded(ACCOUNT);
        assertThat(context.knownAccount()).isNull();

        context.start();
        context.accountLoaded(ACCOUNT);
        assertThat(context.knownAccount()).isEqualTo(ACCOUNT);

        context.clear();
        assertThat(context.knownAccount()).isNull();
    }
}

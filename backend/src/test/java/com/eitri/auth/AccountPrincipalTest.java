package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountPrincipalTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String HASH = "{bcrypt}$2a$10$abcdefghijklmnopqrstuv";

    @Test
    void erasingCredentialsDropsThePasswordHashOnly() {
        AccountPrincipal principal = new AccountPrincipal(ACCOUNT_ID, "johndoe", HASH, Role.USER);

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
        assertThat(principal.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(principal.getUsername()).isEqualTo("johndoe");
        assertThat(principal.role()).isEqualTo(Role.USER);
    }

    @Test
    void toStringNamesTheAccountByIdWithoutCredentialsOrUsername() {
        String printed = new AccountPrincipal(ACCOUNT_ID, "johndoe", HASH, Role.ADMIN).toString();

        assertThat(printed).contains(ACCOUNT_ID.toString(), "ADMIN");
        assertThat(printed).doesNotContain(HASH, "$2a$", "johndoe");
    }

    @Test
    void aRoleChangeKeepsTheIdentityAndCarriesNoCredentials() {
        AccountPrincipal principal = new AccountPrincipal(ACCOUNT_ID, "johndoe", HASH, Role.USER);

        AccountPrincipal promoted = principal.withRole(Role.ADMIN);

        assertThat(promoted.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(promoted.username()).isEqualTo("johndoe");
        assertThat(promoted.role()).isEqualTo(Role.ADMIN);
        assertThat(promoted.getAuthorities()).extracting(Object::toString).containsExactly(Role.ADMIN.authority());
        assertThat(promoted.getPassword()).isNull();
    }

    @Test
    void equalityIgnoresCredentials() {
        AccountPrincipal withHash = new AccountPrincipal(ACCOUNT_ID, "johndoe", HASH, Role.USER);
        AccountPrincipal erased = new AccountPrincipal(ACCOUNT_ID, "johndoe", null, Role.USER);

        assertThat(withHash).isEqualTo(erased).hasSameHashCodeAs(erased);
        assertThat(withHash).isNotEqualTo(withHash.withRole(Role.ADMIN));
    }
}

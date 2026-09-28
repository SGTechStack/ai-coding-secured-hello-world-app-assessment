package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the compact constructor's null-list normalization. */
class LocalUsersPropertiesTest {

    @Test
    void nullUsersListNormalizesToEmptyList() {
        assertThat(new LocalUsersProperties(null).users()).isEmpty();
    }

    @Test
    void nonNullUsersListIsPreservedButUnmodifiable() {
        LocalUsersProperties.UserEntry entry = new LocalUsersProperties.UserEntry("alice", Role.USER, "pw");
        LocalUsersProperties properties = new LocalUsersProperties(List.of(entry));

        assertThat(properties.users()).containsExactly(entry);
        assertThatThrownBy(() -> properties.users().add(entry)).isInstanceOf(UnsupportedOperationException.class);
    }
}

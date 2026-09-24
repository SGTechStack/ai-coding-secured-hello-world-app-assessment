package com.example.helloauth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PasswordResetService#hashToken} is the fixture seam the HTTP tests
 * use to compute expected hashes — so those tests cannot pin the function
 * itself (a mutated hash would satisfy both sides). The known-answer vector
 * here anchors it independently: SHA-256 of {@code "abc"} is the FIPS 180
 * example, lowercase hex.
 */
class PasswordResetServiceTests {

    @Test
    void hashTokenProducesTheSha256HexOfThePlaintext() {
        assertThat(PasswordResetService.hashToken("abc"))
            .isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashTokenHexIsLowercaseAnd64Chars() {
        assertThat(PasswordResetService.hashToken("any-token-value"))
            .hasSize(64)
            .matches("[0-9a-f]+");
    }
}

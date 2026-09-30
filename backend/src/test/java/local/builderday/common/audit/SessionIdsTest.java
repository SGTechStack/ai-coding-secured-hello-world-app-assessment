package local.builderday.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionIdsTest {
  private static final String SESSION_ID = "0f8d1c7e-4a52-4d0e-9d0b-6a1f3c2e9b71";

  @Test
  void should_giveTheSameHash_when_theSecretAndIdentifierAreTheSame() {
    assertThat(new SessionIds("secret-one").hash(SESSION_ID)).isEqualTo(new SessionIds("secret-one").hash(SESSION_ID))
        .matches("[0-9a-f]{64}");
  }

  @Test
  void should_giveADifferentHash_when_theSecretDiffers() {
    assertThat(new SessionIds("secret-one").hash(SESSION_ID)).isNotEqualTo(new SessionIds("secret-two").hash(SESSION_ID));
  }

  @Test
  void should_differFromThePlainSha256_when_hashingAnIdentifier() {
    assertThat(new SessionIds("secret-one").hash(SESSION_ID)).isNotEqualTo(SessionIds.sha256Hex(SESSION_ID));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void should_refuseToDeriveAKey_when_theSecretIsBlank(String secret) {
    assertThatThrownBy(() -> new SessionIds(secret)).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.datasource.password");
  }
}

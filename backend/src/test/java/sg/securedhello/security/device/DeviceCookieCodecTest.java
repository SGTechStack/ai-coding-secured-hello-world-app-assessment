package sg.securedhello.security.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sg.securedhello.testsupport.Proves;

/** The device cookie's value: the id under an HMAC, and nothing verifies that was not signed here (ADR-075). */
class DeviceCookieCodecTest {

    private final DeviceCookieCodec codec = new DeviceCookieCodec(key((byte) 1));

    private static byte[] key(byte seed) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (seed * 31 + i * 7);
        }
        return key;
    }

    @Test
    void aSignedValueVerifiesToItsDevice() {
        UUID device = UUID.randomUUID();
        String value = codec.sign(device);
        assertThat(value).hasSize(DeviceCookieCodec.VALUE_LENGTH).matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}");
        assertThat(codec.verify(value)).contains(device);
        assertThat(codec.sign(device)).as("deterministic").isEqualTo(value);
    }

    @Test
    @Proves("T-LCK-027")
    void aTamperedValueOrOneSignedUnderAnotherKeyIsRefused() {
        UUID device = UUID.randomUUID();
        String value = codec.sign(device);
        String otherId = codec.sign(UUID.randomUUID()).split("\\.")[0];
        assertThat(codec.verify(otherId + value.substring(22))).as("another id under this MAC").isEmpty();
        char changed = value.charAt(30) == 'A' ? 'B' : 'A';
        assertThat(codec.verify(value.substring(0, 30) + changed + value.substring(31))).as("a changed MAC")
                .isEmpty();
        assertThat(codec.verify(new DeviceCookieCodec(key((byte) 2)).sign(device))).as("another key").isEmpty();
    }

    /** The MAC is domain-separated: a bare HMAC of the id under the same key, as another use of it might make, fails. */
    @Test
    void aBareHmacOfTheIdUnderTheSameKeyIsRefused() throws Exception {
        UUID device = UUID.randomUUID();
        String id = codec.sign(device).split("\\.")[0];
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key((byte) 1), "HmacSHA256"));
        String bare = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(Base64.getUrlDecoder().decode(id)));

        assertThat(codec.verify(id + "." + bare)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "x", "not.a.cookie",
            "AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAA!A.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @Proves("T-LCK-027")
    void aMalformedValueIsRefusedWithoutThrowing(String value) {
        assertThat(codec.verify(value)).isEmpty();
    }
}

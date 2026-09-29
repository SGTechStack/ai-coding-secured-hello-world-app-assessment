package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.mfa.TotpSecretCipher.TotpContextMismatchException;
import sg.securedhello.mfa.TotpSecretCipher.UnknownTotpKeyVersionException;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.user.UserAccountRepository;

/** The envelope and its context prefix (level U, ADR-028), and provisioning when encryption fails. */
class TotpSecretCipherTest {

    private static final byte[] KEY = key(7);
    private static final byte[] NEXT_KEY = key(11);

    private static byte[] key(int step) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i * step + 3);
        }
        return key;
    }

    private final AuditEmitter audit = mock(AuditEmitter.class);
    private final BytesEncryptor aesGcm = AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(KEY, "AES")).build();
    private final BytesEncryptor nextAesGcm =
            AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(NEXT_KEY, "AES")).build();
    private final UUID owner = UUID.randomUUID();
    private final byte[] secret = secret();

    private static byte[] secret() {
        byte[] secret = new byte[TotpSecretCipher.SECRET_BYTES];
        Arrays.fill(secret, (byte) 0x5A);
        secret[0] = 1;
        return secret;
    }

    @Test
    void theEnvelopeIsSixtyNineBytesAndOpensToTheSecretForItsOwnRow() {
        TotpSecretCipher cipher = new TotpSecretCipher(Map.of(7, aesGcm), 7, audit);

        byte[] envelope = cipher.seal(owner, secret);

        assertThat(TotpSecretCipher.ENVELOPE_BYTES).isEqualTo(69);
        assertThat(envelope).hasSize(69);
        assertThat(cipher.open(owner, 7, envelope)).isEqualTo(secret);
        assertThat(cipher.keyVersion()).isEqualTo(7);
        verifyNoInteractions(audit);
    }

    @Test
    void anEnvelopeOpenedForAnotherUserIsAMismatchAndIsAudited() {
        TotpSecretCipher cipher = new TotpSecretCipher(Map.of(7, aesGcm), 7, audit);
        UUID other = UUID.randomUUID();
        byte[] envelope = cipher.seal(owner, secret);

        assertThatExceptionOfType(TotpContextMismatchException.class).isThrownBy(() -> cipher.open(other, 7, envelope));
        verify(audit).emit(AuditEvent.TOTP_CONTEXT_MISMATCH, AccountContext.of(other));
    }

    @Test
    void anEnvelopeOpenedUnderAnotherKeyVersionIsAMismatch() {
        // A row whose stored version was changed: the prefix, not the key, is what catches a version replay.
        TotpSecretCipher cipher = new TotpSecretCipher(Map.of(7, aesGcm, 8, aesGcm), 7, audit);
        byte[] envelope = cipher.seal(owner, secret);

        assertThatExceptionOfType(TotpContextMismatchException.class).isThrownBy(() -> cipher.open(owner, 8, envelope));
        verify(audit).emit(AuditEvent.TOTP_CONTEXT_MISMATCH, AccountContext.of(owner));
    }

    @Test
    void afterARotationRowsSealedUnderTheRetiredVersionStillOpenAndNewRowsUseTheCurrentKey() {
        byte[] beforeRotation = new TotpSecretCipher(Map.of(7, aesGcm), 7, audit).seal(owner, secret);
        TotpSecretCipher rotated = new TotpSecretCipher(Map.of(7, aesGcm, 8, nextAesGcm), 8, audit);

        assertThat(rotated.keyVersion()).isEqualTo(8);
        assertThat(rotated.open(owner, 7, beforeRotation)).isEqualTo(secret);
        byte[] afterRotation = rotated.seal(owner, secret);
        assertThat(rotated.open(owner, 8, afterRotation)).isEqualTo(secret);
        // Sealed under the new key, which the retired version's key cannot open.
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> aesGcm.decrypt(afterRotation));
        assertThat(nextAesGcm.decrypt(afterRotation)).hasSize(37);
        verifyNoInteractions(audit);
    }

    @Test
    void aRowOnAVersionWithNoConfiguredKeyIsAnInternalErrorNamingTheVersion() {
        byte[] envelope = new TotpSecretCipher(Map.of(7, aesGcm), 7, audit).seal(owner, secret);
        TotpSecretCipher retiredDropped = new TotpSecretCipher(Map.of(8, nextAesGcm), 8, audit);

        assertThatExceptionOfType(UnknownTotpKeyVersionException.class)
                .isThrownBy(() -> retiredDropped.open(owner, 7, envelope))
                .withMessage("No TOTP key is configured for key version 7");
        verifyNoInteractions(audit);
    }

    @Test
    void theCurrentVersionMustHaveAKey() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TotpSecretCipher(Map.of(7, aesGcm), 8, audit))
                .withMessage("No TOTP key for the current version 8");
    }

    @Test
    void aPlaintextOfTheWrongWidthIsAMismatch() {
        BytesEncryptor shortPlaintext = mock(BytesEncryptor.class);
        when(shortPlaintext.decrypt(any())).thenReturn(new byte[TotpSecretCipher.PREFIX_BYTES]);
        TotpSecretCipher cipher = new TotpSecretCipher(Map.of(7, shortPlaintext), 7, audit);

        assertThatExceptionOfType(TotpContextMismatchException.class)
                .isThrownBy(() -> cipher.open(owner, 7, new byte[69]));
    }

    @Test
    @Proves("T-MFA-022")
    void whenEncryptionFailsProvisioningRaisesAnInternalErrorAndPersistsNoPendingRow() {
        IllegalStateException failure = new IllegalStateException("Unable to invoke Cipher due to bad padding");
        BytesEncryptor failing = mock(BytesEncryptor.class);
        when(failing.encrypt(any())).thenThrow(failure);
        PendingTotpRepository pending = mock(PendingTotpRepository.class);
        TotpUserDetailsRepository factors = mock(TotpUserDetailsRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TotpEnrolment enrolment = new TotpEnrolment(accounts, pending, factors,
                new TotpSecretCipher(Map.of(1, failing), 1, audit), new TotpProperties("Issuer"), audit,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), transactions);

        // Not a mapped refusal: the catch-all advice answers it 500 INTERNAL_ERROR.
        assertThatThrownBy(() -> enrolment.provision(owner, "admin-one")).isSameAs(failure)
                .isNotInstanceOfAny(TotpEnrolment.FactorAlreadyEnrolledException.class,
                        TotpEnrolment.InvalidFactorException.class);
        verify(pending, never()).save(any());
        verifyNoInteractions(factors, accounts, audit);
    }
}

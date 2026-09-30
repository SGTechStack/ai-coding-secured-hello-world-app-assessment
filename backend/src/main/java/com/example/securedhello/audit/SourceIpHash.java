package com.example.securedhello.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.example.securedhello.config.IpHashProperties;

/**
 * {@code source.ip_hash} for audit events: the hex HMAC-SHA-256 of the direct client address with the
 * configured key, so events from one address can be correlated without logging the address.
 * Forwarded headers are ignored.
 * <p>
 * Versioned (IM8 ck-2): every hash travels with {@code source.ip_hash_key_id}, a fingerprint of the
 * key that produced it — the first 64 bits of the HMAC of a fixed label under that key. Rotating the
 * key through the secrets manager therefore changes the id by itself, with no second setting to keep
 * in step, and an investigator can see where a rotation falls in the audit log and which key to hash a
 * suspect address with. The fingerprint is a keyed value, so it reveals nothing about the key, and it
 * never depends on the address.
 */
@Component
public class SourceIpHash {

	/** The fixed label whose HMAC under the key is the key id. Public so a test can derive the id. */
	public static final String KEY_ID_INPUT = "source.ip_hash key id";

	/** Hex characters of the key id: 64 bits, plenty to tell a handful of rotations apart. */
	private static final int KEY_ID_LENGTH = 16;

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	private final String keyId;

	SourceIpHash(IpHashProperties properties) {
		this.key = new SecretKeySpec(properties.key().getBytes(StandardCharsets.UTF_8), ALGORITHM);
		this.keyId = hmac(KEY_ID_INPUT).substring(0, KEY_ID_LENGTH);
	}

	/** The keyed hash of the request's direct client address, with the id of the key that made it. */
	public Keyed of(HttpServletRequest request) {
		return new Keyed(hmac(request.getRemoteAddr()), keyId);
	}

	private String hmac(String input) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every Java platform", ex);
		}
	}

	/**
	 * A client address hash and the id of the key it was made with.
	 * @param value the hex HMAC-SHA-256 of the address, {@code source.ip_hash}
	 * @param keyId the key's fingerprint, {@code source.ip_hash_key_id}
	 */
	public record Keyed(String value, String keyId) {
	}

}

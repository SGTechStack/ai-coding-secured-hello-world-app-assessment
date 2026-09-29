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
 */
@Component
public class SourceIpHash {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	SourceIpHash(IpHashProperties properties) {
		this.key = new SecretKeySpec(properties.key().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	/** The keyed hash of the request's direct client address. */
	public String of(HttpServletRequest request) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(request.getRemoteAddr().getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every Java platform", ex);
		}
	}

}

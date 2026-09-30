package com.example.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.securedhello.config.IpHashProperties;

/**
 * ck-2: {@code source.ip_hash} is versioned by a key id derived from the key itself, so rotating the
 * key through the secrets manager changes the id with no extra configuration to forget, and an
 * investigator can tell which key produced which hash.
 */
class SourceIpHashTest {

	@Test
	void theKeyIdIsStableForOneKeyAndChangesWhenTheKeyIsRotated() {
		SourceIpHash before = new SourceIpHash(new IpHashProperties("synthetic-key-one"));
		SourceIpHash again = new SourceIpHash(new IpHashProperties("synthetic-key-one"));
		SourceIpHash rotated = new SourceIpHash(new IpHashProperties("synthetic-key-two"));

		assertThat(before.of(request("192.0.2.10")).keyId()).matches("[0-9a-f]{16}")
			.isEqualTo(again.of(request("192.0.2.10")).keyId())
			.isNotEqualTo(rotated.of(request("192.0.2.10")).keyId());
	}

	@Test
	void theKeyIdRevealsNeitherTheKeyNorTheAddressHash() {
		SourceIpHash hash = new SourceIpHash(new IpHashProperties("synthetic-key-one"));

		SourceIpHash.Keyed keyed = hash.of(request("192.0.2.10"));

		assertThat(keyed.value()).matches("[0-9a-f]{64}").doesNotStartWith(keyed.keyId());
		assertThat(keyed.keyId()).doesNotContain("synthetic");
		// One key, one id, whatever the address.
		assertThat(hash.of(request("198.51.100.7")).keyId()).isEqualTo(keyed.keyId());
	}

	private static MockHttpServletRequest request(String address) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(address);
		return request;
	}

}

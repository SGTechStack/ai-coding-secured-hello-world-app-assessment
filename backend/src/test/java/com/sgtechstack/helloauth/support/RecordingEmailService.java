package com.sgtechstack.helloauth.support;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sgtechstack.helloauth.passwordreset.EmailService;

public final class RecordingEmailService implements EmailService {

	private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

	@Override
	public void sendPasswordResetEmail(String toEmail, String username, URI resetLink) {
		this.sent.add(new SentEmail(toEmail, username, resetLink));
	}

	public List<SentEmail> sentTo(String email) {
		return this.sent.stream().filter(mail -> mail.to().equals(email)).toList();
	}

	public Optional<SentEmail> lastSentTo(String email) {
		List<SentEmail> mails = sentTo(email);
		return mails.isEmpty() ? Optional.empty() : Optional.of(mails.getLast());
	}

	public record SentEmail(String to, String username, URI link) {

		public String token() {
			String fragment = this.link.getFragment();
			if (fragment == null || !fragment.startsWith("token=")) {
				throw new IllegalStateException("Reset link has no token fragment: " + this.link);
			}
			return fragment.substring("token=".length());
		}

	}

}

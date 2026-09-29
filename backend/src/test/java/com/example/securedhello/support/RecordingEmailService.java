package com.example.securedhello.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.securedhello.notification.EmailService;

/**
 * Test seam for notifications (spec Seam 3): records every email instead of writing it to the email
 * file. Import {@link Config} to make it the application's {@code EmailService}.
 */
public final class RecordingEmailService implements EmailService {

	/** One recorded email: which operation and to whom. */
	public record Sent(String operation, String to) {
	}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();

	@Override
	public void notifyAccountLocked(String to) {
		sent.add(new Sent("account-locked", to));
	}

	@Override
	public void notifyPasswordChanged(String to) {
		sent.add(new Sent("password-changed", to));
	}

	public List<Sent> sent() {
		return List.copyOf(sent);
	}

	public void clear() {
		sent.clear();
	}

	/** Replaces the application's {@code EmailService} with one shared {@link RecordingEmailService}. */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		RecordingEmailService recordingEmailService() {
			return new RecordingEmailService();
		}

	}

}

package com.example.securedhello.support;

import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.securedhello.notification.EmailService;

/**
 * Test seam for notifications (spec Seam 3): records every email instead of writing it to the email
 * file. Import {@link Config} to make it the application's {@code EmailService}. Reset-link emails
 * are also captured with their full link, so a test can pull the Reset Token out of it; because reset
 * emails are sent in the background, {@link #await(Predicate)} waits for them with a bounded timeout.
 */
public final class RecordingEmailService implements EmailService {

	/** One recorded email: which operation and to whom. */
	public record Sent(String operation, String to) {
	}

	private static final Pattern TOKEN_FRAGMENT = Pattern.compile("#token=(.+)$");

	private final List<Sent> sent = new CopyOnWriteArrayList<>();

	private final List<String> resetLinks = new CopyOnWriteArrayList<>();

	/** Armed by {@link #blockNextResetLink()}, so a test can prove work done before this point runs
	 * strictly before this email is recorded, deterministically rather than by a timing guess. */
	private final AtomicReference<CountDownLatch> gate = new AtomicReference<>();

	@Override
	public void notifyAccountLocked(String to) {
		sent.add(new Sent("account-locked", to));
	}

	@Override
	public void notifyPasswordChanged(String to) {
		sent.add(new Sent("password-changed", to));
	}

	@Override
	public void sendPasswordResetLink(String to, String resetLink) {
		awaitGate();
		sent.add(new Sent("password-reset-link", to));
		resetLinks.add(resetLink);
	}

	@Override
	public void notifyPasswordResetCompleted(String to) {
		sent.add(new Sent("password-reset-completed", to));
	}

	public List<Sent> sent() {
		return List.copyOf(sent);
	}

	/** Every reset link recorded so far, oldest first. */
	public List<String> resetLinks() {
		return List.copyOf(resetLinks);
	}

	/** The Reset Token carried by the given link's {@code #token=} fragment. */
	public static String tokenFrom(String resetLink) {
		Matcher matcher = TOKEN_FRAGMENT.matcher(resetLink);
		if (!matcher.find()) {
			return fail("No #token= fragment in reset link: " + resetLink);
		}
		return matcher.group(1);
	}

	/** Waits (bounded) for the one reset link a single request is expected to have issued. */
	public String awaitResetLink() {
		List<String> links = awaitResetLinks(1);
		return links.get(links.size() - 1);
	}

	/**
	 * Waits (bounded) until at least {@code minCount} reset links have been recorded, then returns
	 * every one so far, oldest first: for tests that need to tell an earlier issued token from a
	 * later one that cancelled it.
	 */
	public List<String> awaitResetLinks(int minCount) {
		for (int attempt = 0; attempt < 50; attempt++) {
			List<String> links = resetLinks();
			if (links.size() >= minCount) {
				return links;
			}
			pause();
		}
		return fail("Expected at least " + minCount + " password-reset link(s) to be recorded");
	}

	/** Waits (bounded) for an email matching the filter, since some emails are sent in the background. */
	public Sent await(Predicate<Sent> filter) {
		for (int attempt = 0; attempt < 50; attempt++) {
			Optional<Sent> match = sent.stream().filter(filter).findFirst();
			if (match.isPresent()) {
				return match.get();
			}
			pause();
		}
		return fail("Expected email not recorded");
	}

	/**
	 * Blocks the next {@link #sendPasswordResetLink} until {@link #releaseResetLink()}: lets a test
	 * prove that a response already returned before this email was recorded, deterministically.
	 */
	public void blockNextResetLink() {
		gate.set(new CountDownLatch(1));
	}

	/** Releases a reset-link email blocked by {@link #blockNextResetLink()}, if any is waiting. */
	public void releaseResetLink() {
		CountDownLatch latch = gate.getAndSet(null);
		if (latch != null) {
			latch.countDown();
		}
	}

	private void awaitGate() {
		CountDownLatch latch = gate.get();
		if (latch == null) {
			return;
		}
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void pause() {
		try {
			Thread.sleep(100);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public void clear() {
		sent.clear();
		resetLinks.clear();
		releaseResetLink();
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

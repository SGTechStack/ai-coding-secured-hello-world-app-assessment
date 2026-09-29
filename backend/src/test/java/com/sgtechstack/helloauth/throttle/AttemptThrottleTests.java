package com.sgtechstack.helloauth.throttle;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sgtechstack.helloauth.support.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptThrottleTests {

	private final MutableClock clock = new MutableClock();

	private final AttemptThrottle throttle = new AttemptThrottle(3, Duration.ofMinutes(15), this.clock);

	@Test
	void allowsUpToTheLimitThenReportsTimeUntilTheWindowEnds() {
		assertThat(this.throttle.tryAcquire("ip")).isEmpty();
		assertThat(this.throttle.tryAcquire("ip")).isEmpty();
		this.clock.advance(Duration.ofMinutes(5));
		assertThat(this.throttle.tryAcquire("ip")).isEmpty();

		assertThat(this.throttle.tryAcquire("ip")).contains(Duration.ofMinutes(10));
	}

	@Test
	void windowReopensAfterItEnds() {
		for (int i = 0; i < 3; i++) {
			this.throttle.tryAcquire("ip");
		}
		this.clock.advance(Duration.ofMinutes(15));

		assertThat(this.throttle.tryAcquire("ip")).isEmpty();
	}

	@Test
	void keysAreIndependent() {
		for (int i = 0; i < 3; i++) {
			this.throttle.tryAcquire("a");
		}

		assertThat(this.throttle.tryAcquire("a")).isPresent();
		assertThat(this.throttle.tryAcquire("b")).isEmpty();
	}

	@Test
	void releasedAttemptsDoNotCount() {
		for (int i = 0; i < 10; i++) {
			assertThat(this.throttle.tryAcquire("ip")).isEmpty();
			this.throttle.release("ip");
		}
	}

	@Test
	void concurrentAttemptsNeverExceedTheLimit() throws Exception {
		AttemptThrottle throttle = new AttemptThrottle(50, Duration.ofMinutes(15), this.clock);
		AtomicInteger allowed = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
			for (int i = 0; i < 500; i++) {
				pool.submit(() -> {
					start.await();
					if (throttle.tryAcquire("ip").isEmpty()) {
						allowed.incrementAndGet();
					}
					return null;
				});
			}
			start.countDown();
		}

		assertThat(allowed).hasValue(50);
	}

}

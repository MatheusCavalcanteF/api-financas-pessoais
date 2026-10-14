package com.finance.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.finance.api.MutableClock;

class FailureLockoutTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T12:00:00Z"));
	private final FailureLockout lockout = new FailureLockout(5, Duration.ofMinutes(15), 100, clock);

	@Test
	void parallelAttemptsNeverTestMoreThanMaxFailures() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger allowed = new AtomicInteger();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 50; i++) {
				executor.submit(() -> {
					start.await();
					try {
						lockout.acquire("email+ip");
						allowed.incrementAndGet();
					}
					catch (RateLimitExceededException ex) {
						// esperado depois da 5ª
					}
					return null;
				});
			}
			start.countDown();
		}

		assertThat(allowed).hasValue(5);
	}

	@Test
	void counterExpiresWithoutNewFailures() {
		for (int i = 0; i < 4; i++) {
			lockout.acquire("email+ip");
		}

		clock.advance(Duration.ofMinutes(15));
		assertThat(lockout.trackedKeys()).isZero();

		// Recomeça do zero: mais 4 não bloqueiam
		for (int i = 0; i < 4; i++) {
			lockout.acquire("email+ip");
		}
	}

	@Test
	void resetClearsTheBlock() {
		for (int i = 0; i < 5; i++) {
			lockout.acquire("email+ip");
		}
		assertThatThrownBy(() -> lockout.acquire("email+ip")).isInstanceOf(RateLimitExceededException.class);

		lockout.reset("email+ip");
		lockout.acquire("email+ip");
	}

	@Test
	void numberOfKeysIsBounded() {
		for (int i = 0; i < 10_000; i++) {
			lockout.acquire("email+ip-" + i);
		}

		assertThat(lockout.trackedKeys()).isLessThanOrEqualTo(100);
	}

}

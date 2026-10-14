package com.finance.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finance.api.MutableClock;

class SlidingWindowRateLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T12:00:00Z"));

	@Test
	void windowSlidesInsteadOfResettingAtFixedBoundaries() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofMinutes(1), 100, clock);
		limiter.acquire("ip"); // 0s
		clock.advance(Duration.ofSeconds(30));
		limiter.acquire("ip"); // 30s

		clock.advance(Duration.ofSeconds(29)); // 59s
		assertRetryAfter(limiter, 1);

		clock.advance(Duration.ofSeconds(1)); // 60s: a de 0s saiu da janela
		limiter.acquire("ip");

		clock.advance(Duration.ofSeconds(1)); // 61s: a de 30s ainda conta
		assertRetryAfter(limiter, 29);
	}

	@Test
	void keysExpireOnTheirOwn() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, Duration.ofMinutes(1), 100, clock);
		limiter.acquire("a");
		limiter.acquire("b");
		assertThat(limiter.trackedKeys()).isEqualTo(2);

		clock.advance(Duration.ofMinutes(1));
		assertThat(limiter.trackedKeys()).isZero();
	}

	@Test
	void numberOfKeysIsBounded() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, Duration.ofHours(1), 100, clock);
		for (int i = 0; i < 10_000; i++) {
			limiter.acquire("ip-" + i);
		}

		assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(100);
	}

	private static void assertRetryAfter(SlidingWindowRateLimiter limiter, long seconds) {
		assertThatThrownBy(() -> limiter.acquire("ip")).isInstanceOfSatisfying(RateLimitExceededException.class,
				ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(seconds));
	}

}

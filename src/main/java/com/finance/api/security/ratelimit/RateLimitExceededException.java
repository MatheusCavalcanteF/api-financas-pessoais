package com.finance.api.security.ratelimit;

import java.time.Duration;

/**
 * Limite de tentativas estourado (429). {@link #getRetryAfterSeconds()} vai no header Retry-After.
 */
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(Duration retryAfter) {
		this(ceilSeconds(retryAfter));
	}

	private RateLimitExceededException(long retryAfterSeconds) {
		super("Tente novamente em " + describe(retryAfterSeconds) + ".");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	// Para cima: quem obedece o Retry-After nunca volta antes de liberar
	private static long ceilSeconds(Duration retryAfter) {
		long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
		return Math.max(seconds, 1);
	}

	private static String describe(long seconds) {
		if (seconds < 60) {
			return seconds + (seconds == 1 ? " segundo" : " segundos");
		}
		long minutes = (seconds + 59) / 60;
		return minutes + (minutes == 1 ? " minuto" : " minutos");
	}

}

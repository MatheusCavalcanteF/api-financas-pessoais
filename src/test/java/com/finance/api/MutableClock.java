package com.finance.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio parado que só anda com {@link #advance}: testa expiração sem sleep.
 */
public class MutableClock extends Clock {

	private volatile Instant now;

	public MutableClock(Instant start) {
		this.now = start;
	}

	public synchronized void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

}

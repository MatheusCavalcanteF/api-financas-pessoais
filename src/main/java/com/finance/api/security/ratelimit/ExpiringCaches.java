package com.finance.api.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

final class ExpiringCaches {

	private ExpiringCaches() {
	}

	/**
	 * Cache com tamanho máximo que descarta a entrada {@code expireAfterWrite} depois da
	 * última escrita. O tempo vem do mesmo {@link Clock} que decide os bloqueios: nos testes,
	 * avançar o relógio expira os dois juntos.
	 */
	static <K, V> Cache<K, V> create(int maximumSize, Duration expireAfterWrite, Clock clock) {
		return Caffeine.newBuilder()
			.maximumSize(maximumSize)
			.expireAfterWrite(expireAfterWrite)
			.ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
			.build();
	}

}

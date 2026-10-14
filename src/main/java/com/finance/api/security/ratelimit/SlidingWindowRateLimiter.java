package com.finance.api.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * No máximo {@code limit} tentativas por chave em qualquer intervalo de {@code window}.
 * <p>
 * Janela deslizante exata: guarda o horário das últimas {@code limit} tentativas aceitas.
 * Uma janela fixa deixaria passar o dobro na virada (20 no fim de um minuto e 20 no começo
 * do próximo). Tentativas recusadas não entram, então o Retry-After é o tempo real até liberar.
 */
public class SlidingWindowRateLimiter {

	private final int limit;
	private final long windowMillis;
	private final Clock clock;
	// Uma janela inteira sem tentativa aceita e a chave some
	private final Cache<String, long[]> attempts;

	public SlidingWindowRateLimiter(int limit, Duration window, int maxTrackedKeys, Clock clock) {
		this.limit = limit;
		this.windowMillis = window.toMillis();
		this.clock = clock;
		this.attempts = ExpiringCaches.create(maxTrackedKeys, window, clock);
	}

	/**
	 * Registra uma tentativa da chave.
	 *
	 * @throws RateLimitExceededException se a chave já usou o limite da janela
	 */
	public void acquire(String key) {
		// compute é atômico por chave: requisições simultâneas não passam juntas do limite
		attempts.asMap().compute(key, (k, times) -> {
			long now = clock.millis();
			Duration retryAfter = retryAfter(times, now);
			if (retryAfter.isPositive()) {
				// Exceção dentro do compute: o Caffeine mantém a entrada como estava
				throw new RateLimitExceededException(retryAfter);
			}
			return append(times, now);
		});
	}

	/** Quanto falta para a chave poder tentar de novo, sem registrar tentativa; zero se já pode. */
	public Duration retryAfter(String key) {
		return retryAfter(attempts.getIfPresent(key), clock.millis());
	}

	long trackedKeys() {
		attempts.cleanUp();
		return attempts.estimatedSize();
	}

	// Limite usado e a mais antiga ainda na janela: espera até ela sair
	private Duration retryAfter(long[] times, long now) {
		if (times == null || times.length < limit) {
			return Duration.ZERO;
		}
		return Duration.ofMillis(Math.max(times[0] + windowMillis - now, 0));
	}

	// Array novo a cada tentativa (no máximo `limit` posições), da mais antiga para a mais recente
	private long[] append(long[] times, long now) {
		if (times == null) {
			return new long[] { now };
		}
		long[] updated = times.length < limit ? Arrays.copyOf(times, times.length + 1)
				: Arrays.copyOfRange(times, 1, limit + 1);
		updated[updated.length - 1] = now;
		return updated;
	}

}

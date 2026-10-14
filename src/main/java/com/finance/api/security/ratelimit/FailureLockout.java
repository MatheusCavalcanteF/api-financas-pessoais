package com.finance.api.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Bloqueia a chave por {@code lockDuration} depois de {@code maxFailures} falhas seguidas.
 * <p>
 * A tentativa já conta como falha no {@link #acquire}, antes de a senha ser conferida, e só
 * o {@link #reset} (login com sucesso) desfaz. Contar depois deixaria várias requisições em
 * paralelo passarem pela checagem antes da primeira falha ser registrada, testando mais de
 * {@code maxFailures} senhas.
 */
public class FailureLockout {

	private final int maxFailures;
	private final Duration lockDuration;
	private final Clock clock;
	// A expiração conta da última falha: sem falha nova por lockDuration, o contador zera.
	// A escrita que bloqueia é a última, então o bloqueio acaba junto com a entrada.
	private final Cache<String, Failures> failures;

	public FailureLockout(int maxFailures, Duration lockDuration, int maxTrackedKeys, Clock clock) {
		this.maxFailures = maxFailures;
		this.lockDuration = lockDuration;
		this.clock = clock;
		this.failures = ExpiringCaches.create(maxTrackedKeys, lockDuration, clock);
	}

	/**
	 * Conta uma tentativa da chave como falha até que {@link #reset} diga o contrário.
	 *
	 * @throws RateLimitExceededException se a chave está bloqueada
	 */
	public void acquire(String key) {
		failures.asMap().compute(key, (k, current) -> {
			Instant now = clock.instant();
			if (current != null && current.blockedUntil() != null) {
				// Exceção dentro do compute: o Caffeine mantém a entrada (e a expiração) como estava
				throw new RateLimitExceededException(Duration.between(now, current.blockedUntil()));
			}
			int count = current == null ? 1 : current.count() + 1;
			return new Failures(count, count >= maxFailures ? now.plus(lockDuration) : null);
		});
	}

	/**
	 * Quanto falta para o bloqueio da chave acabar, sem contar tentativa; zero se não está
	 * bloqueada. É só leitura: quem decide e conta continua sendo o {@link #acquire}.
	 */
	public Duration remainingBlock(String key) {
		Failures current = failures.getIfPresent(key);
		if (current == null || current.blockedUntil() == null) {
			return Duration.ZERO;
		}
		return Duration.between(clock.instant(), current.blockedUntil());
	}

	public void reset(String key) {
		failures.invalidate(key);
	}

	long trackedKeys() {
		failures.cleanUp();
		return failures.estimatedSize();
	}

	private record Failures(int count, Instant blockedUntil) {
	}

}

package com.finance.api.security.ratelimit;

import java.time.Duration;
import java.util.List;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Limites do /auth. Os padrões ficam no application.yml; valor ausente ou não positivo
 * derruba a inicialização em vez de subir sem limite.
 *
 * @param trustedProxies IPs/CIDRs dos proxies cujo X-Forwarded-For é lido (veja {@link ClientIpResolver})
 * @param maxTrackedKeys teto de chaves em memória por contador
 */
@Validated
@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(
		List<String> trustedProxies,
		@Positive int maxTrackedKeys,
		@NotNull @Valid Login login,
		@NotNull @Valid Register register) {

	public RateLimitProperties {
		trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
	}

	public record Login(
			@Positive int maxFailures,
			@NotNull @DurationMin(seconds = 1) Duration lockDuration,
			@Positive int maxAttemptsPerIp,
			@NotNull @DurationMin(seconds = 1) Duration attemptsWindow) {
	}

	public record Register(
			@Positive int maxPerIp,
			@NotNull @DurationMin(seconds = 1) Duration window) {
	}

}

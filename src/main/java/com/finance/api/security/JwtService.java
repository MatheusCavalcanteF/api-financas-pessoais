package com.finance.api.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.finance.api.user.Role;
import com.finance.api.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final String ISSUER = "api-financas-pessoais";
	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_ROLE = "role";
	private static final int MIN_SECRET_BYTES = 32; // 256 bits

	private final SecretKey key;
	private final Duration expiration;
	private final JwtParser parser;

	public JwtService(JwtProperties properties) {
		this.key = Keys.hmacShaKeyFor(decodeSecret(properties.secret()));
		this.expiration = validateExpiration(properties.expiration());
		this.parser = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build();
	}

	public String generateToken(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.issuer(ISSUER)
				.subject(user.getId().toString())
				.claim(CLAIM_EMAIL, user.getEmail())
				.claim(CLAIM_ROLE, user.getRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	/**
	 * Valida assinatura, emissor e expiração e devolve o usuário do token.
	 *
	 * @throws JwtException se o token for inválido por qualquer motivo
	 */
	public AuthenticatedUser parseToken(String token) {
		Claims claims = parser.parseSignedClaims(token).getPayload();
		try {
			return new AuthenticatedUser(
					UUID.fromString(claims.getSubject()),
					claims.get(CLAIM_EMAIL, String.class),
					Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new JwtException("Claims do token inválidas", ex);
		}
	}

	public Duration getExpiration() {
		return expiration;
	}

	// Fail-fast: segredo ausente ou fraco derruba a inicialização em vez de
	// subir uma API emitindo tokens forjáveis.
	private static byte[] decodeSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"security.jwt.secret não configurado. Defina a variável de ambiente JWT_SECRET.");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(secret.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("security.jwt.secret deve estar codificado em Base64.", ex);
		}
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("security.jwt.secret precisa ter no mínimo 256 bits (32 bytes); tem "
					+ (bytes.length * 8) + " bits. Gere um com: openssl rand -base64 64");
		}
		return bytes;
	}

	private static Duration validateExpiration(Duration expiration) {
		if (expiration == null || expiration.isNegative() || expiration.isZero()) {
			throw new IllegalStateException("security.jwt.expiration deve ser uma duração positiva (ex: PT2H).");
		}
		return expiration;
	}

}

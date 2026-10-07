package com.finance.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.finance.api.user.Role;
import com.finance.api.user.User;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

	private static final String SECRET = Base64.getEncoder().encodeToString(new byte[64]);

	private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(2)));

	@Test
	void generatedTokenRoundTripsToAuthenticatedUser() {
		User user = user();

		AuthenticatedUser parsed = jwtService.parseToken(jwtService.generateToken(user));

		assertThat(parsed).isEqualTo(new AuthenticatedUser(user.getId(), "ana@email.com", Role.USER));
	}

	@Test
	void rejectsTokenSignedWithAnotherKey() {
		byte[] otherKey = new byte[64];
		otherKey[0] = 1;
		var other = new JwtService(
				new JwtProperties(Base64.getEncoder().encodeToString(otherKey), Duration.ofHours(2)));

		String token = other.generateToken(user());

		assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsExpiredToken() {
		var shortLived = new JwtService(new JwtProperties(SECRET, Duration.ofMillis(1)));
		String token = shortLived.generateToken(user());

		assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void failsFastWhenSecretIsMissing() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties("  ", Duration.ofHours(2))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void failsFastWhenSecretIsShorterThan256Bits() {
		String weak = Base64.getEncoder().encodeToString(new byte[31]);

		assertThatThrownBy(() -> new JwtService(new JwtProperties(weak, Duration.ofHours(2))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("256 bits");
	}

	@Test
	void failsFastWhenSecretIsNotBase64() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties("não-é-base64!!", Duration.ofHours(2))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Base64");
	}

	private static User user() {
		User user = new User("ana@email.com", "hash", Role.USER);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}

}

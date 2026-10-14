package com.finance.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.finance.api.security.ratelimit.FailureLockout;
import com.finance.api.security.ratelimit.RateLimitExceededException;
import com.finance.api.security.ratelimit.RateLimitProperties;
import com.finance.api.security.ratelimit.SlidingWindowRateLimiter;

/**
 * Limites de tentativa do /auth, conferidos antes de qualquer acesso ao banco:
 * <ul>
 * <li>login: no máximo {@code max-attempts-per-ip} tentativas por IP na janela, qualquer e-mail;</li>
 * <li>login: {@code max-failures} falhas seguidas do mesmo e-mail + IP bloqueiam o par por
 * {@code lock-duration}. O bloqueio vale igual para e-mail cadastrado ou não, então o 429
 * não revela se a conta existe;</li>
 * <li>cadastro: no máximo {@code max-per-ip} tentativas por IP na janela.</li>
 * </ul>
 * Todos lançam {@link RateLimitExceededException}.
 */
@Component
public class AuthRateLimiter {

	private final SlidingWindowRateLimiter loginAttemptsPerIp;
	private final FailureLockout loginLockout;
	private final SlidingWindowRateLimiter registrationsPerIp;

	public AuthRateLimiter(RateLimitProperties properties, Clock clock) {
		RateLimitProperties.Login login = properties.login();
		RateLimitProperties.Register register = properties.register();
		int maxKeys = properties.maxTrackedKeys();

		this.loginAttemptsPerIp = new SlidingWindowRateLimiter(login.maxAttemptsPerIp(), login.attemptsWindow(),
				maxKeys, clock);
		this.loginLockout = new FailureLockout(login.maxFailures(), login.lockDuration(), maxKeys, clock);
		this.registrationsPerIp = new SlidingWindowRateLimiter(register.maxPerIp(), register.window(), maxKeys,
				clock);
	}

	/**
	 * Conta a tentativa como falha; {@link #loginSucceeded} zera o contador se a senha estiver certa.
	 * <p>
	 * O bloqueio do par vem primeiro, só lendo: com o par bloqueado a tentativa não gasta o limite
	 * do IP, e se o IP também estourou o Retry-After é o mais longo dos dois. Quem espera o tempo
	 * informado não leva outro 429 na volta. A falha só é contada depois de o IP aceitar, no
	 * {@link FailureLockout#acquire} (confere e conta de uma vez): tentativa recusada pelo IP não
	 * chega à senha e por isso não conta.
	 */
	public void acquireLogin(String email, String clientIp) {
		String key = loginKey(email, clientIp);
		Duration blocked = loginLockout.remainingBlock(key);
		if (blocked.isPositive()) {
			Duration ipRetryAfter = loginAttemptsPerIp.retryAfter(clientIp);
			throw new RateLimitExceededException(blocked.compareTo(ipRetryAfter) >= 0 ? blocked : ipRetryAfter);
		}
		loginAttemptsPerIp.acquire(clientIp);
		loginLockout.acquire(key);
	}

	public void loginSucceeded(String email, String clientIp) {
		loginLockout.reset(loginKey(email, clientIp));
	}

	public void acquireRegistration(String clientIp) {
		registrationsPerIp.acquire(clientIp);
	}

	// Hash de tamanho fixo: o e-mail do login não tem limite de tamanho, e como chave em
	// texto deixaria alguém ocupar memória mandando e-mails enormes. O IP vem primeiro
	// porque nunca contém o separador.
	private static String loginKey(String email, String clientIp) {
		byte[] input = (clientIp + "\n" + AuthService.normalizeEmail(email)).getBytes(StandardCharsets.UTF_8);
		return HexFormat.of().formatHex(sha256().digest(input));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			// Toda JVM é obrigada a suportar SHA-256
			throw new IllegalStateException(ex);
		}
	}

}

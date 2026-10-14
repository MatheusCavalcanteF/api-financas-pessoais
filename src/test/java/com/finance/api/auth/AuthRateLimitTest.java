package com.finance.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.finance.api.MutableClock;
import com.finance.api.TestcontainersConfiguration;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;

/**
 * Rate limit do /auth com os limites de produção do application.yml (5 falhas, 15 min,
 * 20/min, 5/hora). Nada é desligado nem afrouxado para o teste.
 * <p>
 * Os contadores vivem em beans do contexto, que o Spring reaproveita entre os testes. Por
 * isso cada teste usa um IP só dele (e e-mails só dele): um teste nunca herda tentativas de
 * outro, sem reset e sem depender da ordem. O relógio é um {@link MutableClock}: o tempo
 * fica parado e só anda quando o teste manda, então o Retry-After é exato e a expiração é
 * testada sem sleep.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "security.rate-limit.trusted-proxies=" + AuthRateLimitTest.PROXY_CIDR)
@AutoConfigureMockMvc
class AuthRateLimitTest {

	static final String PROXY_CIDR = "203.0.113.0/24";
	private static final String PROXY_IP = "203.0.113.10";
	private static final String PASSWORD = "senha-correta-123";
	private static final String WRONG_PASSWORD = "senha-errada-123";
	private static final String LOCK_SECONDS = "900";

	private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();
	private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-13T12:00:00Z"));

	@TestBean
	private Clock clock;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// O JUnit cria uma instância por teste: cada teste tem o seu IP
	private final String ip = nextIp();
	private String email;

	static Clock clock() {
		return CLOCK;
	}

	@BeforeEach
	void setUp() {
		email = newEmail();
		userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), Role.USER));
	}

	// ------------------------------------------------------------ bloqueio por e-mail + IP

	@Test
	void fiveConsecutiveFailuresBlockTheEmailAndIp() throws Exception {
		failLogins(5, email);

		login(email, WRONG_PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, LOCK_SECONDS));
	}

	@Test
	void correctPasswordDuringBlockStillGets429() throws Exception {
		failLogins(5, email);

		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, LOCK_SECONDS));
	}

	@Test
	void blockedResponseIsTheSameForExistingAndUnknownEmail() throws Exception {
		String unknown = newEmail();
		failLogins(5, email);
		failLogins(5, unknown);

		MockHttpServletResponse existing = login(email, WRONG_PASSWORD).andReturn().getResponse();
		MockHttpServletResponse missing = login(unknown, WRONG_PASSWORD).andReturn().getResponse();

		assertThat(existing.getStatus()).isEqualTo(429);
		assertThat(missing.getStatus()).isEqualTo(existing.getStatus());
		assertThat(missing.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo(existing.getHeader(HttpHeaders.RETRY_AFTER));
		assertThat(missing.getContentAsString()).isEqualTo(existing.getContentAsString());
	}

	@Test
	void successfulLoginResetsTheFailureCounter() throws Exception {
		failLogins(4, email);
		login(email, PASSWORD).andExpect(status().isOk());

		// Sem o reset, a primeira destas já seria a 5ª falha seguida e a seguinte, 429
		failLogins(4, email);
		login(email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void blockDoesNotAffectAnotherIp() throws Exception {
		failLogins(5, email);

		mockMvc.perform(loginRequest(email, PASSWORD).with(from(nextIp()))).andExpect(status().isOk());
	}

	@Test
	void blockExpiresAfterFifteenMinutes() throws Exception {
		failLogins(5, email);

		CLOCK.advance(Duration.ofMinutes(15).minusSeconds(1));
		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));

		CLOCK.advance(Duration.ofSeconds(1));
		login(email, PASSWORD).andExpect(status().isOk());
	}

	// ------------------------------------------------------------ resposta 429

	@Test
	void tooManyRequestsIsProblemDetailWithRetryAfter() throws Exception {
		failLogins(5, email);

		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, LOCK_SECONDS))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.title").value("Muitas tentativas"))
			.andExpect(jsonPath("$.detail").value("Tente novamente em 15 minutos."));
	}

	// ------------------------------------------------------------ limite geral por IP

	@Test
	void loginAttemptsPerIpAreLimitedAcrossEmails() throws Exception {
		for (int i = 0; i < 20; i++) {
			login(newEmail(), WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
			.andExpect(jsonPath("$.detail").value("Tente novamente em 1 minuto."));

		CLOCK.advance(Duration.ofMinutes(1));
		login(email, PASSWORD).andExpect(status().isOk());
	}

	// ------------------------------------------------------------ os dois limites juntos

	@Test
	void blockedPairWithIpLimitExceededAnswersWithTheBlock() throws Exception {
		failLogins(5, email);
		// Completa as 20 do minuto com outros e-mails
		for (int i = 0; i < 15; i++) {
			login(newEmail(), WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(newEmail(), WRONG_PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, LOCK_SECONDS))
			.andExpect(jsonPath("$.detail").value("Tente novamente em 15 minutos."));

		CLOCK.advance(Duration.ofMinutes(15));
		login(email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void ipLimitWinsWhenItOutlastsTheBlock() throws Exception {
		failLogins(5, email);
		// Faltam 30 s de bloqueio; as 5 falhas já saíram da janela do IP
		CLOCK.advance(Duration.ofMinutes(15).minusSeconds(30));
		for (int i = 0; i < 20; i++) {
			login(newEmail(), WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

		CLOCK.advance(Duration.ofSeconds(60));
		login(email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void attemptsOnABlockedPairDoNotSpendTheIpLimit() throws Exception {
		failLogins(5, email);
		for (int i = 0; i < 20; i++) {
			login(email, PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, LOCK_SECONDS));
		}

		// Só as 5 falhas gastaram o limite do IP: outro e-mail ainda chega à senha
		login(newEmail(), WRONG_PASSWORD).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------ cadastro

	@Test
	void sixthRegistrationWithinTheHourGets429() throws Exception {
		for (int i = 0; i < 5; i++) {
			register(newEmail()).andExpect(status().isCreated());
		}

		register(newEmail()).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"))
			.andExpect(jsonPath("$.title").value("Muitas tentativas"));

		CLOCK.advance(Duration.ofHours(1));
		register(newEmail()).andExpect(status().isCreated());
	}

	// ------------------------------------------------------------ IP do cliente

	@Test
	void forwardedForIsIgnoredWhenRemoteAddrIsNotTrusted() throws Exception {
		// Cada tentativa inventa um IP diferente; se o header valesse, nenhuma bloquearia
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(loginRequest(email, WRONG_PASSWORD).with(from(ip)).header("X-Forwarded-For", nextIp()))
				.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(loginRequest(email, PASSWORD).with(from(ip)).header("X-Forwarded-For", nextIp()))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, LOCK_SECONDS));
	}

	@Test
	void forwardedForIsUsedWhenRemoteAddrIsTrustedProxy() throws Exception {
		String client = nextIp();
		// 6.6.6.6 é o que o cliente inventou; o proxy acrescentou o IP real à direita
		String forwardedFor = "6.6.6.6, " + client;

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(loginRequest(email, WRONG_PASSWORD).with(from(PROXY_IP))
				.header("X-Forwarded-For", forwardedFor)).andExpect(status().isUnauthorized());
		}

		mockMvc.perform(loginRequest(email, PASSWORD).with(from(PROXY_IP)).header("X-Forwarded-For", forwardedFor))
			.andExpect(status().isTooManyRequests());

		// Mesmo proxy, outro cliente: contador separado
		mockMvc.perform(loginRequest(email, PASSWORD).with(from(PROXY_IP)).header("X-Forwarded-For", nextIp()))
			.andExpect(status().isOk());
	}

	// ------------------------------------------------------------ helpers

	private void failLogins(int times, String email) throws Exception {
		for (int i = 0; i < times; i++) {
			login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(loginRequest(email, password).with(from(ip)));
	}

	private ResultActions register(String email) throws Exception {
		return mockMvc.perform(post("/auth/register").with(from(ip))
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentialsJson(email, PASSWORD)));
	}

	private static MockHttpServletRequestBuilder loginRequest(String email, String password) {
		return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentialsJson(email, password));
	}

	private static String credentialsJson(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

	private static RequestPostProcessor from(String remoteAddr) {
		return request -> {
			request.setRemoteAddr(remoteAddr);
			return request;
		};
	}

	// 10.x.y.z: fora do CIDR de proxy confiável deste teste
	private static String nextIp() {
		int n = IP_SEQUENCE.incrementAndGet();
		return "10." + (n >> 16 & 0xFF) + "." + (n >> 8 & 0xFF) + "." + (n & 0xFF);
	}

	private static String newEmail() {
		return "rate-limit-" + UUID.randomUUID() + "@email.com";
	}

}

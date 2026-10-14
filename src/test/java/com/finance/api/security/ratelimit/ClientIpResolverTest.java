package com.finance.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	private static final String PROXY_CIDR = "203.0.113.0/24";
	private static final String PROXY = "203.0.113.10";
	private static final String CLIENT = "198.51.100.7";

	@Test
	void withoutTrustedProxiesForwardedForIsIgnored() {
		assertThat(resolver().resolve(request("127.0.0.1", "6.6.6.6"))).isEqualTo("127.0.0.1");
	}

	@Test
	void forwardedForIsIgnoredWhenRemoteAddrIsNotTrusted() {
		assertThat(resolver(PROXY_CIDR).resolve(request(CLIENT, "6.6.6.6"))).isEqualTo(CLIENT);
	}

	@Test
	void trustedProxyUsesRightmostEntry() {
		// 6.6.6.6 veio do cliente; o proxy acrescentou o IP que viu à direita
		assertThat(resolver(PROXY_CIDR).resolve(request(PROXY, "6.6.6.6, " + CLIENT))).isEqualTo(CLIENT);
	}

	@Test
	void trustedHopsAreSkipped() {
		assertThat(resolver(PROXY_CIDR, "10.0.0.0/8").resolve(request(PROXY, "6.6.6.6, " + CLIENT + ", 10.0.0.2")))
			.isEqualTo(CLIENT);
	}

	@Test
	void multipleHeaderLinesAreReadInOrder() {
		assertThat(resolver(PROXY_CIDR).resolve(request(PROXY, "6.6.6.6", CLIENT))).isEqualTo(CLIENT);
	}

	@Test
	void trustedProxyWithoutForwardedForFallsBackToRemoteAddr() {
		assertThat(resolver(PROXY_CIDR).resolve(request(PROXY))).isEqualTo(PROXY);
	}

	@Test
	void onlyTrustedHopsFallsBackToRemoteAddr() {
		assertThat(resolver(PROXY_CIDR, "10.0.0.0/8").resolve(request(PROXY, "10.0.0.2"))).isEqualTo(PROXY);
	}

	@Test
	void entryThatIsNotAnIpStopsTheChain() {
		// Nada à esquerda de uma entrada inválida é confiável, nem o 6.6.6.6
		assertThat(resolver(PROXY_CIDR).resolve(request(PROXY, "6.6.6.6, unknown"))).isEqualTo(PROXY);
	}

	@Test
	void hostNamesAreNeverResolved() {
		assertThat(resolver(PROXY_CIDR).resolve(request(PROXY, "localhost"))).isEqualTo(PROXY);
	}

	@Test
	void ipv6IsNormalizedSoTheSameAddressHasOneCounter() {
		assertThat(resolver(PROXY_CIDR).resolve(request(PROXY, "2001:DB8::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
		assertThat(resolver().resolve(request("2001:db8:0:0:0:0:0:1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
	}

	@Test
	void ipv6TrustedProxy() {
		assertThat(resolver("::1").resolve(request("0:0:0:0:0:0:0:1", CLIENT))).isEqualTo(CLIENT);
	}

	@Test
	void invalidTrustedProxyFailsFast() {
		assertThatThrownBy(() -> resolver("proxy.interno")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("proxy.interno");
		assertThatThrownBy(() -> resolver("10.0.0.0/33")).isInstanceOf(IllegalStateException.class);
	}

	private static ClientIpResolver resolver(String... trustedProxies) {
		return new ClientIpResolver(new RateLimitProperties(List.of(trustedProxies), 100,
				new RateLimitProperties.Login(5, Duration.ofMinutes(15), 20, Duration.ofMinutes(1)),
				new RateLimitProperties.Register(5, Duration.ofHours(1))));
	}

	private static MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		for (String value : forwardedFor) {
			request.addHeader("X-Forwarded-For", value);
		}
		return request;
	}

}

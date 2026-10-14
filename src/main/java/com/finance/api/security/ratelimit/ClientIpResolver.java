package com.finance.api.security.ratelimit;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.security.util.matcher.InetAddressMatcher;
import org.springframework.security.util.matcher.InetAddressMatchers;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP do cliente para o rate limit.
 * <p>
 * O padrão é {@code getRemoteAddr()}: o X-Forwarded-For pode ser escrito por qualquer um.
 * O header só é lido quando a conexão vem de um proxy de
 * {@code security.rate-limit.trusted-proxies}, e mesmo assim só a parte que os proxies
 * confiáveis acrescentaram: a lista é percorrida da direita para a esquerda até o primeiro
 * endereço que não é de proxy confiável. Tudo à esquerda dele veio do cliente.
 * <p>
 * Por isso o {@code server.forward-headers-strategy} fica desligado: ele confiaria no
 * header vindo de qualquer IP de rede interna, onde o próprio Next roda.
 */
@Component
public class ClientIpResolver {

	private static final String X_FORWARDED_FOR = "X-Forwarded-For";

	private final List<InetAddressMatcher> trustedProxies;

	public ClientIpResolver(RateLimitProperties properties) {
		this.trustedProxies = properties.trustedProxies()
			.stream()
			.map(String::trim)
			.filter(proxy -> !proxy.isEmpty())
			.map(ClientIpResolver::parseTrustedProxy)
			.toList();
	}

	public String resolve(HttpServletRequest request) {
		String remoteAddr = request.getRemoteAddr();
		InetAddress remote = parseLiteral(remoteAddr);
		if (remote == null) {
			return remoteAddr;
		}
		if (!isTrusted(remote)) {
			return remote.getHostAddress();
		}

		for (String hop : forwardedFor(request).reversed()) {
			InetAddress address = parseLiteral(hop);
			if (address == null) {
				// Um proxy confiável sempre acrescenta um IP; lixo aqui significa que a cadeia
				// não é confiável a partir deste ponto
				break;
			}
			if (!isTrusted(address)) {
				return address.getHostAddress();
			}
		}
		return remote.getHostAddress();
	}

	private boolean isTrusted(InetAddress address) {
		return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
	}

	// Várias linhas do header equivalem a uma só separada por vírgulas, na mesma ordem
	private static List<String> forwardedFor(HttpServletRequest request) {
		return Collections.list(request.getHeaders(X_FORWARDED_FOR))
			.stream()
			.flatMap(header -> Arrays.stream(header.split(",")))
			.map(String::trim)
			.filter(hop -> !hop.isEmpty())
			.toList();
	}

	// Só aceita IP literal: nunca resolve nome via DNS. Normaliza a escrita, para
	// "2001:DB8::1" e "2001:db8:0:0:0:0:0:1" caírem no mesmo contador.
	private static InetAddress parseLiteral(String value) {
		if (value == null) {
			return null;
		}
		try {
			return InetAddress.ofLiteral(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static InetAddressMatcher parseTrustedProxy(String proxy) {
		try {
			return InetAddressMatchers.fromIpAddress(proxy);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("security.rate-limit.trusted-proxies tem uma entrada inválida: '" + proxy
					+ "'. Use IPs ou CIDRs, ex.: 10.0.0.5,10.1.0.0/16", ex);
		}
	}

}

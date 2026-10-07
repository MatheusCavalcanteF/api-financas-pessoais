package com.finance.api.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Não é um @Component de propósito: o Boot registraria o filtro também na cadeia
 * do servlet container, fora da cadeia do Spring Security, e ele rodaria antes do
 * SecurityContextHolderFilter (que descartaria a autenticação). Ele é instanciado
 * só no {@link SecurityConfig}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			chain.doFilter(request, response);
			return;
		}

		try {
			AuthenticatedUser user = jwtService.parseToken(header.substring(BEARER_PREFIX.length()).trim());
			var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
			authentication.setDetails(detailsSource.buildDetails(request));

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		}
		catch (JwtException | IllegalArgumentException ex) {
			// Token inválido não é erro aqui: a requisição segue anônima e, se a rota
			// exigir autenticação, o AuthenticationEntryPoint responde 401.
			SecurityContextHolder.clearContext();
		}

		chain.doFilter(request, response);
	}

}

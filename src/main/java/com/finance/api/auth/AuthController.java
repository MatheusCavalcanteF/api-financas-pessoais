package com.finance.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finance.api.auth.dto.LoginRequest;
import com.finance.api.auth.dto.RegisterRequest;
import com.finance.api.auth.dto.TokenResponse;
import com.finance.api.auth.dto.UserResponse;
import com.finance.api.security.ratelimit.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthRateLimiter rateLimiter;
	private final ClientIpResolver clientIpResolver;

	public AuthController(AuthService authService, AuthRateLimiter rateLimiter, ClientIpResolver clientIpResolver) {
		this.authService = authService;
		this.rateLimiter = rateLimiter;
		this.clientIpResolver = clientIpResolver;
	}

	// Os limites vêm antes do AuthService: um 429 não consulta o banco nem a senha.
	// Corpo inválido (400) é recusado antes e não conta.

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
		rateLimiter.acquireRegistration(clientIpResolver.resolve(httpRequest));
		return authService.register(request);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String clientIp = clientIpResolver.resolve(httpRequest);
		rateLimiter.acquireLogin(request.email(), clientIp);
		TokenResponse token = authService.login(request);
		rateLimiter.loginSucceeded(request.email(), clientIp);
		return token;
	}

}

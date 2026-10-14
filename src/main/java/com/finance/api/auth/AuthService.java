package com.finance.api.auth;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.auth.dto.LoginRequest;
import com.finance.api.auth.dto.RegisterRequest;
import com.finance.api.auth.dto.TokenResponse;
import com.finance.api.auth.dto.UserResponse;
import com.finance.api.security.JwtService;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	// Hash usado quando o e-mail não existe, para o login levar o mesmo tempo
	// nos dois casos e não revelar quais e-mails estão cadastrados.
	private final String dummyHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.dummyHash = passwordEncoder.encode("timing-attack-mitigation");
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyInUseException();
		}

		// Role nunca vem do payload: todo cadastro público é USER
		User user = new User(email, passwordEncoder.encode(request.password()), Role.USER);
		try {
			// flush para a violação de unicidade (cadastro concorrente) estourar aqui
			return UserResponse.from(userRepository.saveAndFlush(user));
		}
		catch (DataIntegrityViolationException ex) {
			throw new EmailAlreadyInUseException();
		}
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
		String hash = user.map(User::getPassword).orElse(dummyHash);

		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !passwordMatches) {
			throw new BadCredentialsException("E-mail ou senha inválidos");
		}

		String token = jwtService.generateToken(user.get());
		return TokenResponse.bearer(token, jwtService.getExpiration().toSeconds());
	}

	// Também usado pelo AuthRateLimiter: o contador de falhas segue a mesma identidade do login
	static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}

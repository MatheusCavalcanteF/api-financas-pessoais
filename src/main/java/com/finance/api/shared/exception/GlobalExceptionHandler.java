package com.finance.api.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.finance.api.auth.EmailAlreadyInUseException;
import com.finance.api.transaction.InvalidImportFileException;

/**
 * Padroniza todas as respostas de erro como ProblemDetail (RFC 7807).
 * Erros do Spring MVC (400, 404, 405, 415...) são tratados pela classe base.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}

		ProblemDetail problem = ex.getBody();
		problem.setTitle("Dados inválidos");
		problem.setDetail("Um ou mais campos são inválidos");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(EmailAlreadyInUseException.class)
	ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
		return problem(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
	}

	@ExceptionHandler(InvalidImportFileException.class)
	ProblemDetail handleInvalidImportFile(InvalidImportFileException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Arquivo inválido", ex.getMessage());
		problem.setProperty("errors", ex.getErrors());
		return problem;
	}

	// ?sort= com propriedade inexistente; sem isso viraria 500
	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleInvalidSort(PropertyReferenceException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Ordenação inválida",
				"Propriedade de ordenação inexistente: " + ex.getPropertyName());
	}

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail handleBadCredentials(BadCredentialsException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", ex.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthentication(AuthenticationException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Não autenticado", "Token ausente, inválido ou expirado");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para acessar este recurso");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Erro não tratado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}

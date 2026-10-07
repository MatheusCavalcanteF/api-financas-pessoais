package com.finance.api.shared.exception;

/**
 * Violação de regra de negócio que conflita com o estado atual do recurso (409).
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}

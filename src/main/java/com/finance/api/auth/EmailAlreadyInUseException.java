package com.finance.api.auth;

public class EmailAlreadyInUseException extends RuntimeException {

	public EmailAlreadyInUseException() {
		super("E-mail já cadastrado");
	}

}

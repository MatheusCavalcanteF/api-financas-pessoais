package com.finance.api.shared.exception;

/**
 * Recurso inexistente ou de outro usuário. Os dois casos respondem 404 de
 * propósito: um 403 confirmaria que o id existe.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource) {
		super(resource + " não encontrado(a)");
	}

}

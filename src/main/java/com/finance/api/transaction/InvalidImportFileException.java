package com.finance.api.transaction;

import java.util.List;

/**
 * Arquivo de importação com erros de formato ou conteúdo. Nenhuma linha é
 * gravada: o usuário corrige e reenvia (a importação é idempotente).
 */
public class InvalidImportFileException extends RuntimeException {

	private final List<String> errors;

	public InvalidImportFileException(List<String> errors) {
		super("O arquivo contém erros e nenhuma transação foi importada");
		this.errors = List.copyOf(errors);
	}

	public InvalidImportFileException(String error) {
		this(List.of(error));
	}

	public List<String> getErrors() {
		return errors;
	}

}

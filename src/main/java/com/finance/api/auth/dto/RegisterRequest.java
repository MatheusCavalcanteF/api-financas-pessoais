package com.finance.api.auth.dto;

import com.finance.api.shared.validation.MaxBytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8) @MaxBytes(72) String password) {

	// Evita que a senha vaze em logs caso o objeto seja logado
	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", password=***]";
	}

}

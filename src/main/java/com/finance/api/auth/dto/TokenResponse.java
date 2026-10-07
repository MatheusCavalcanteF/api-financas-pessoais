package com.finance.api.auth.dto;

/**
 * @param expiresIn validade do token em segundos
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String accessToken, long expiresIn) {
		return new TokenResponse(accessToken, "Bearer", expiresIn);
	}

}

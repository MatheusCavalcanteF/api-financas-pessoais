package com.finance.api.transaction;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finance.api.security.AuthenticatedUser;
import com.finance.api.transaction.dto.RetroactiveCategorizationResponse;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Faz parte da API de regras (por isso o caminho), mas mora aqui junto do serviço:
 * ver {@link RetroactiveCategorizationService}.
 */
@RestController
@Tag(name = "category-rule-controller")
public class RetroactiveCategorizationController {

	private final RetroactiveCategorizationService categorizationService;

	public RetroactiveCategorizationController(RetroactiveCategorizationService categorizationService) {
		this.categorizationService = categorizationService;
	}

	/**
	 * Aplica as regras atuais às transações ainda sem categoria. Nunca troca uma
	 * categoria existente, e repetir a chamada sem regras novas atualiza zero.
	 */
	@PostMapping("/category-rules/apply-retroactive")
	public RetroactiveCategorizationResponse applyRetroactive(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return new RetroactiveCategorizationResponse(categorizationService.apply(user.id()));
	}

}

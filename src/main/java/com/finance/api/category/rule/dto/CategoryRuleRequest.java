package com.finance.api.category.rule.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param keyword    trecho procurado na descrição, sem diferenciar maiúsculas nem acentos
 * @param categoryId precisa pertencer ao usuário autenticado
 */
public record CategoryRuleRequest(
		@NotNull
		@Size(max = 100)
		// Keyword curta casaria com quase toda descrição
		@Pattern(regexp = "(?s).*\\S.*\\S.*\\S.*", message = "deve ter ao menos 3 caracteres além de espaços")
		String keyword,
		@NotNull UUID categoryId) {
}

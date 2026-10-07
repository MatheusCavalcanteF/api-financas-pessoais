package com.finance.api.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.finance.api.transaction.TransactionType;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param amount     valor sempre positivo; o sentido vem de {@code type}
 * @param categoryId opcional; precisa pertencer ao usuário autenticado
 */
public record TransactionRequest(
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
		@NotNull LocalDate date,
		@NotBlank @Size(max = 255) String description,
		@NotNull TransactionType type,
		UUID categoryId) {
}

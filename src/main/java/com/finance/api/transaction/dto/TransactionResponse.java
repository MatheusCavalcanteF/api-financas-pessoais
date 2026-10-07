package com.finance.api.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.finance.api.transaction.Transaction;
import com.finance.api.transaction.TransactionType;

// uniqueHash fica de fora: é detalhe interno da deduplicação
public record TransactionResponse(UUID id, BigDecimal amount, LocalDate date, String description,
		TransactionType type, UUID categoryId, Instant createdAt) {

	public static TransactionResponse from(Transaction transaction) {
		return new TransactionResponse(transaction.getId(), transaction.getAmount(), transaction.getDate(),
				transaction.getDescription(), transaction.getType(), transaction.getCategoryId(),
				transaction.getCreatedAt());
	}

}

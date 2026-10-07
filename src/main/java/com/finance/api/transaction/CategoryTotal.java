package com.finance.api.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Soma das transações de um tipo em uma categoria, agregada no banco.
 *
 * @param categoryId   {@code null} para transações sem categoria
 * @param categoryName {@code null} junto com {@code categoryId}
 */
public record CategoryTotal(TransactionType type, UUID categoryId, String categoryName, BigDecimal total) {
}

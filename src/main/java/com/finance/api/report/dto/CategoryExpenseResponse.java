package com.finance.api.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param categoryId {@code null} para o grupo "Sem categoria"; use-o, e não o nome,
 *                   para distinguir esse grupo de uma categoria com o mesmo nome
 */
public record CategoryExpenseResponse(UUID categoryId, String categoryName, BigDecimal total) {
}

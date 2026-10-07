package com.finance.api.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param balance            {@code totalIncome - totalExpense}; negativo se gastou mais do que recebeu
 * @param expensesByCategory do maior para o menor total; a soma dos totais é {@code totalExpense}
 */
public record MonthlyReportResponse(int year, int month, BigDecimal totalIncome, BigDecimal totalExpense,
		BigDecimal balance, List<CategoryExpenseResponse> expensesByCategory) {
}

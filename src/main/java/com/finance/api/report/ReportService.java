package com.finance.api.report;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.report.dto.CategoryExpenseResponse;
import com.finance.api.report.dto.MonthlyReportResponse;
import com.finance.api.transaction.CategoryTotal;
import com.finance.api.transaction.TransactionRepository;
import com.finance.api.transaction.TransactionType;

@Service
public class ReportService {

	static final String UNCATEGORIZED = "Sem categoria";

	// Escala da coluna: mês vazio responde 0.00, como os demais valores
	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final TransactionRepository transactionRepository;

	public ReportService(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	/**
	 * A agregação é feita no banco; aqui só se separam as poucas linhas que voltam
	 * (no máximo duas por categoria) em receitas e despesas.
	 */
	@Transactional(readOnly = true)
	public MonthlyReportResponse monthly(YearMonth period, UUID userId) {
		// Intervalo semiaberto: usa o índice (user_id, transaction_date) sem calcular o último dia
		List<CategoryTotal> totals = transactionRepository.sumByTypeAndCategory(userId, period.atDay(1),
				period.plusMonths(1).atDay(1));

		BigDecimal income = ZERO;
		BigDecimal expense = ZERO;
		List<CategoryExpenseResponse> expensesByCategory = new ArrayList<>();
		for (CategoryTotal total : totals) {
			if (total.type() == TransactionType.INCOME) {
				income = income.add(total.total());
			}
			else {
				expense = expense.add(total.total());
				String name = total.categoryId() == null ? UNCATEGORIZED : total.categoryName();
				expensesByCategory.add(new CategoryExpenseResponse(total.categoryId(), name, total.total()));
			}
		}
		return new MonthlyReportResponse(period.getYear(), period.getMonthValue(), income, expense,
				income.subtract(expense), expensesByCategory);
	}

}

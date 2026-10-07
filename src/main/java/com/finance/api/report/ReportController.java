package com.finance.api.report;

import java.time.YearMonth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finance.api.report.dto.MonthlyReportResponse;
import com.finance.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	/**
	 * Receitas, despesas, saldo e despesas por categoria do mês, para os gráficos
	 * do dashboard. Fora do intervalo, ano e mês respondem 400 em vez de 500.
	 */
	@GetMapping("/monthly")
	public MonthlyReportResponse monthly(
			@RequestParam @Min(value = 1900, message = "deve estar entre 1900 e 9999")
			@Max(value = 9999, message = "deve estar entre 1900 e 9999") int year,
			@RequestParam @Min(value = 1, message = "deve estar entre 1 e 12")
			@Max(value = 12, message = "deve estar entre 1 e 12") int month,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return reportService.monthly(YearMonth.of(year, month), user.id());
	}

}

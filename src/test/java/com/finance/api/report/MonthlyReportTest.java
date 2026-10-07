package com.finance.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.finance.api.TestcontainersConfiguration;
import com.finance.api.category.CategoryService;
import com.finance.api.category.dto.CategoryRequest;
import com.finance.api.report.dto.CategoryExpenseResponse;
import com.finance.api.report.dto.MonthlyReportResponse;
import com.finance.api.security.JwtService;
import com.finance.api.transaction.TransactionService;
import com.finance.api.transaction.TransactionType;
import com.finance.api.transaction.dto.TransactionRequest;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MonthlyReportTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Autowired
	private ReportService reportService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private MockMvc mockMvc;

	private User user;
	private UUID groceries;
	private UUID transport;

	@BeforeEach
	void setUp() {
		user = newUser();
		groceries = category(user, "Mercado");
		transport = category(user, "Transporte");

		income(user, "5000.00", LocalDate.of(2026, 10, 5), null);
		income(user, "300.00", LocalDate.of(2026, 10, 20), category(user, "Freela"));
		expense(user, "120.50", LocalDate.of(2026, 10, 1), groceries); // 1º dia entra
		expense(user, "79.50", LocalDate.of(2026, 10, 15), groceries);
		expense(user, "50.00", LocalDate.of(2026, 10, 31), transport); // último dia entra
		expense(user, "30.00", LocalDate.of(2026, 10, 10), null); // importada, sem categoria

		// Fora do mês, coladas nas bordas
		expense(user, "999.00", LocalDate.of(2026, 9, 30), groceries);
		expense(user, "999.00", LocalDate.of(2026, 11, 1), groceries);
	}

	@Test
	void aggregatesTheMonthWithUncategorizedExpensesAndBoundaries() {
		MonthlyReportResponse report = reportService.monthly(OCTOBER, user.getId());

		assertThat(report.year()).isEqualTo(2026);
		assertThat(report.month()).isEqualTo(10);
		assertThat(report.totalIncome()).isEqualByComparingTo("5300.00");
		assertThat(report.totalExpense()).isEqualByComparingTo("280.00");
		assertThat(report.balance()).isEqualByComparingTo("5020.00");
		// Maior total primeiro; receitas não entram, nem a categoria Freela
		assertThat(report.expensesByCategory()).containsExactly(
				new CategoryExpenseResponse(groceries, "Mercado", new BigDecimal("200.00")),
				new CategoryExpenseResponse(transport, "Transporte", new BigDecimal("50.00")),
				new CategoryExpenseResponse(null, ReportService.UNCATEGORIZED, new BigDecimal("30.00")));
	}

	@Test
	void categoryTotalsAlwaysAddUpToTotalExpense() {
		MonthlyReportResponse report = reportService.monthly(OCTOBER, user.getId());

		BigDecimal sum = report.expensesByCategory()
			.stream()
			.map(CategoryExpenseResponse::total)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(sum).isEqualByComparingTo(report.totalExpense());
	}

	@Test
	void ignoresOtherUsersData() {
		User other = newUser();
		UUID otherGroceries = category(other, "Mercado");
		expense(other, "777.00", LocalDate.of(2026, 10, 10), otherGroceries);
		income(other, "777.00", LocalDate.of(2026, 10, 10), null);

		MonthlyReportResponse report = reportService.monthly(OCTOBER, user.getId());

		assertThat(report.totalIncome()).isEqualByComparingTo("5300.00");
		assertThat(report.totalExpense()).isEqualByComparingTo("280.00");
		assertThat(reportService.monthly(OCTOBER, other.getId()).expensesByCategory())
			.containsExactly(new CategoryExpenseResponse(otherGroceries, "Mercado", new BigDecimal("777.00")));
	}

	@Test
	void emptyMonthReturnsZerosInsteadOfNulls() {
		MonthlyReportResponse report = reportService.monthly(YearMonth.of(2026, 12), user.getId());

		assertThat(report).isEqualTo(new MonthlyReportResponse(2026, 12, new BigDecimal("0.00"),
				new BigDecimal("0.00"), new BigDecimal("0.00"), List.of()));
	}

	@Test
	void negativeBalanceWhenSpendingMoreThanEarning() {
		User spender = newUser();
		income(spender, "100.00", LocalDate.of(2026, 10, 1), null);
		expense(spender, "250.00", LocalDate.of(2026, 10, 2), null);

		assertThat(reportService.monthly(OCTOBER, spender.getId()).balance()).isEqualByComparingTo("-150.00");
	}

	@Test
	void endpointUsesTheAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/reports/monthly").param("year", "2026")
			.param("month", "10")
			.header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalIncome").value(5300.0))
			.andExpect(jsonPath("$.totalExpense").value(280.0))
			.andExpect(jsonPath("$.balance").value(5020.0))
			.andExpect(jsonPath("$.expensesByCategory.length()").value(3))
			.andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Mercado"))
			.andExpect(jsonPath("$.expensesByCategory[2].categoryId").doesNotExist());

		// Outro usuário, mesmo mês: não vê nada
		mockMvc.perform(get("/reports/monthly").param("year", "2026")
			.param("month", "10")
			.header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalExpense").value(0.0))
			.andExpect(jsonPath("$.expensesByCategory").isEmpty());
	}

	@Test
	void endpointRejectsInvalidPeriodAndMissingToken() throws Exception {
		mockMvc.perform(get("/reports/monthly").param("year", "2026")
			.param("month", "13")
			.header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Dados inválidos"))
			.andExpect(jsonPath("$.errors.month").value("deve estar entre 1 e 12"));

		mockMvc.perform(get("/reports/monthly").param("month", "10").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/reports/monthly").param("year", "2026").param("month", "abc")
			.header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/reports/monthly").param("year", "2026").param("month", "10"))
			.andExpect(status().isUnauthorized());
	}

	private UUID category(User owner, String name) {
		return categoryService.create(new CategoryRequest(name), owner.getId()).id();
	}

	private void income(User owner, String amount, LocalDate date, UUID categoryId) {
		transaction(owner, amount, date, categoryId, TransactionType.INCOME);
	}

	private void expense(User owner, String amount, LocalDate date, UUID categoryId) {
		transaction(owner, amount, date, categoryId, TransactionType.EXPENSE);
	}

	private void transaction(User owner, String amount, LocalDate date, UUID categoryId, TransactionType type) {
		transactionService.create(new TransactionRequest(new BigDecimal(amount), date, "Lançamento " + amount,
				type, categoryId), owner.getId());
	}

	private User newUser() {
		return userRepository.save(new User(UUID.randomUUID() + "@email.com", "hash-irrelevante", Role.USER));
	}

	private String bearer(User owner) {
		return "Bearer " + jwtService.generateToken(owner);
	}

}

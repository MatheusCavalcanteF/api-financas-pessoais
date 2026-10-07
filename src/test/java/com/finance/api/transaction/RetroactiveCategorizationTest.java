package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.finance.api.TestcontainersConfiguration;
import com.finance.api.category.CategoryService;
import com.finance.api.category.dto.CategoryRequest;
import com.finance.api.category.rule.CategoryRuleService;
import com.finance.api.category.rule.dto.CategoryRuleRequest;
import com.finance.api.security.JwtService;
import com.finance.api.transaction.dto.TransactionRequest;
import com.finance.api.transaction.dto.TransactionResponse;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RetroactiveCategorizationTest {

	@Autowired
	private RetroactiveCategorizationService categorizationService;

	@Autowired
	private TransactionImportService importService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private CategoryRuleService ruleService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private MockMvc mockMvc;

	private User user;
	private UUID transport;
	private UUID groceries;
	private UUID leisure;

	@BeforeEach
	void setUp() {
		user = newUser();
		transport = category(user, "Transporte");
		groceries = category(user, "Mercado");
		leisure = category(user, "Lazer");

		// Importado antes de existir qualquer regra: tudo chega sem categoria
		importCsv(user, """
				date,amount,description,type
				2026-10-01,25.00,UBER *VIAGEM,EXPENSE
				2026-10-02,312.40,SUPERMERCADO EXTRA,EXPENSE
				2026-10-02,200.00,Posto Shell,EXPENSE
				""");
		// Manual sem categoria também entra; manual já categorizado não
		manual(user, "Mercado da esquina", null);
		manual(user, "Uber com amigos", leisure);
	}

	@Test
	void categorizesOnlyUncategorizedTransactionsThatMatch() {
		rule(user, "uber", transport);
		rule(user, "mercado", groceries);

		assertThat(categorizationService.apply(user.getId())).isEqualTo(3);

		assertThat(categoriesByDescription(user)).containsOnly(
				Map.entry("UBER *VIAGEM", Optional.of(transport)),
				Map.entry("SUPERMERCADO EXTRA", Optional.of(groceries)),
				Map.entry("Mercado da esquina", Optional.of(groceries)),
				Map.entry("Posto Shell", Optional.empty()),
				// A regra "uber" casa, mas a escolha do usuário prevalece
				Map.entry("Uber com amigos", Optional.of(leisure)));
	}

	@Test
	void runningAgainWithoutNewRulesUpdatesNothing() {
		rule(user, "uber", transport);
		categorizationService.apply(user.getId());

		assertThat(categorizationService.apply(user.getId())).isZero();

		rule(user, "posto", transport);
		assertThat(categorizationService.apply(user.getId())).isEqualTo(1);
	}

	@Test
	void withoutRulesNothingChanges() {
		assertThat(categorizationService.apply(user.getId())).isZero();
		assertThat(categoriesByDescription(user).values()).filteredOn(Optional::isPresent).hasSize(1);
	}

	@Test
	void neverTouchesAnotherUsersTransactionsOrUsesTheirRules() {
		User other = newUser();
		importCsv(other, """
				date,amount,description,type
				2026-10-01,30.00,UBER *VIAGEM,EXPENSE
				""");
		rule(other, "posto", category(other, "Combustível"));
		rule(user, "uber", transport);

		categorizationService.apply(user.getId());
		assertThat(categoriesByDescription(other)).containsOnly(Map.entry("UBER *VIAGEM", Optional.empty()));

		assertThat(categorizationService.apply(other.getId())).isZero();
		assertThat(categoriesByDescription(user)).containsEntry("Posto Shell", Optional.empty());
	}

	@Test
	void endpointReturnsTheUpdatedCountForTheAuthenticatedUser() throws Exception {
		rule(user, "uber", transport);

		mockMvc.perform(post("/category-rules/apply-retroactive").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updatedCount").value(1));

		mockMvc.perform(post("/category-rules/apply-retroactive")).andExpect(status().isUnauthorized());
	}

	private UUID category(User owner, String name) {
		return categoryService.create(new CategoryRequest(name), owner.getId()).id();
	}

	private void rule(User owner, String keyword, UUID categoryId) {
		ruleService.create(new CategoryRuleRequest(keyword, categoryId), owner.getId());
	}

	private void manual(User owner, String description, UUID categoryId) {
		transactionService.create(new TransactionRequest(new BigDecimal("10.00"), LocalDate.of(2026, 10, 3),
				description, TransactionType.EXPENSE, categoryId), owner.getId());
	}

	private void importCsv(User owner, String csv) {
		importService.importFile(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), owner.getId());
	}

	private Map<String, Optional<UUID>> categoriesByDescription(User owner) {
		return transactionService.list(owner.getId(), Pageable.unpaged())
			.stream()
			.collect(Collectors.toMap(TransactionResponse::description, t -> Optional.ofNullable(t.categoryId())));
	}

	private User newUser() {
		return userRepository.save(new User(UUID.randomUUID() + "@email.com", "hash-irrelevante", Role.USER));
	}

	private String bearer(User owner) {
		return "Bearer " + jwtService.generateToken(owner);
	}

}

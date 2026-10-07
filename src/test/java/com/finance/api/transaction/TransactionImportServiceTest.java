package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.finance.api.TestcontainersConfiguration;
import com.finance.api.security.JwtService;
import com.finance.api.transaction.dto.ImportSummaryResponse;
import com.finance.api.transaction.dto.TransactionRequest;
import com.finance.api.transaction.dto.TransactionResponse;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionImportServiceTest {

	private static final String HEADER = "date,amount,description,type\n";

	@Autowired
	private TransactionImportService importService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private MockMvc mockMvc;

	private User user;

	// Usuário novo por teste: os testes não compartilham dados e dispensam limpeza
	@BeforeEach
	void createUser() {
		user = newUser();
	}

	@Test
	void importsNewLinesAndSkipsExistingOnes() {
		// Já lançado à mão antes da importação do extrato
		transactionService.create(new TransactionRequest(new BigDecimal("8.00"), LocalDate.of(2026, 10, 1),
				"Café", TransactionType.EXPENSE, null), user.getId());

		ImportSummaryResponse summary = importCsv(HEADER + """
				2026-10-01,8.00,CAFÉ,EXPENSE
				2026-10-02,5000.00,Salário,INCOME
				2026-10-03,120.50,Mercado,EXPENSE
				""");

		assertThat(summary).isEqualTo(new ImportSummaryResponse(3, 2, 1));
		assertThat(descriptionsOf(user)).containsExactlyInAnyOrder("Café", "Salário", "Mercado");
	}

	@Test
	void reimportingTheSameFileSkipsEveryLine() {
		String csv = HEADER + """
				2026-10-02,5000.00,Salário,INCOME
				2026-10-03,120.50,Mercado,EXPENSE
				""";

		assertThat(importCsv(csv)).isEqualTo(new ImportSummaryResponse(2, 2, 0));
		assertThat(importCsv(csv)).isEqualTo(new ImportSummaryResponse(2, 0, 2));
		assertThat(descriptionsOf(user)).hasSize(2);
	}

	@Test
	void identicalLinesInTheSameFileAreDistinctTransactions() {
		String csv = HEADER + """
				2026-10-01,8.00,Café,EXPENSE
				2026-10-01,8.00,Café,EXPENSE
				""";

		assertThat(importCsv(csv)).isEqualTo(new ImportSummaryResponse(2, 2, 0));
		assertThat(importCsv(csv)).isEqualTo(new ImportSummaryResponse(2, 0, 2));
	}

	@Test
	void extractWithOneMoreOccurrenceImportsOnlyTheNewOne() {
		importCsv(HEADER + "2026-10-01,8.00,Café,EXPENSE\n");

		ImportSummaryResponse summary = importCsv(HEADER + """
				2026-10-01,8.00,Café,EXPENSE
				2026-10-01,8.00,Café,EXPENSE
				""");

		assertThat(summary).isEqualTo(new ImportSummaryResponse(2, 1, 1));
	}

	@Test
	void duplicatesAreScopedPerUser() {
		String csv = HEADER + "2026-10-02,5000.00,Salário,INCOME\n";
		importCsv(csv);

		ImportSummaryResponse summary = importService.importCsv(stream(csv), newUser().getId());

		assertThat(summary).isEqualTo(new ImportSummaryResponse(1, 1, 0));
	}

	@Test
	void anyInvalidLineRejectsTheWholeFile() {
		String csv = HEADER + """
				2026-10-02,5000.00,Salário,INCOME
				02/10/2026,10.00,Data errada,EXPENSE
				2026-10-03,-5.00,Valor negativo,EXPENSE
				2026-10-03,5.00,Tipo errado,TRANSFER
				""";

		assertThatThrownBy(() -> importCsv(csv)).isInstanceOfSatisfying(InvalidImportFileException.class,
				ex -> assertThat(ex.getErrors()).hasSize(3)
					.anySatisfy(e -> assertThat(e).startsWith("Linha 3:").contains("data inválida"))
					.anySatisfy(e -> assertThat(e).startsWith("Linha 4:").contains("positivo"))
					.anySatisfy(e -> assertThat(e).startsWith("Linha 5:").contains("tipo inválido")));
		assertThat(descriptionsOf(user)).isEmpty();
	}

	@Test
	void acceptsBomQuotedCommasAnyColumnOrderAndCase() {
		String csv = "﻿Type,DATE,description,amount\n" + """
				expense,2026-10-01,"Padaria, Pão",12.5

				""";

		assertThat(importCsv(csv)).isEqualTo(new ImportSummaryResponse(1, 1, 0));
		TransactionResponse saved = transactionService.list(user.getId(), Pageable.unpaged()).getContent().getFirst();
		assertThat(saved.description()).isEqualTo("Padaria, Pão");
		assertThat(saved.amount()).isEqualByComparingTo("12.50");
		assertThat(saved.type()).isEqualTo(TransactionType.EXPENSE);
		assertThat(saved.categoryId()).isNull();
	}

	@Test
	void rejectsMissingColumnsAndNonUtf8Files() {
		assertThatThrownBy(() -> importCsv("date,amount,description\n2026-10-01,8.00,Café\n"))
			.isInstanceOfSatisfying(InvalidImportFileException.class,
					ex -> assertThat(ex.getErrors().getFirst()).contains("type"));

		byte[] latin1 = (HEADER + "2026-10-01,8.00,Café,EXPENSE\n").getBytes(StandardCharsets.ISO_8859_1);
		assertThatThrownBy(() -> importService.importCsv(new ByteArrayInputStream(latin1), user.getId()))
			.isInstanceOfSatisfying(InvalidImportFileException.class,
					ex -> assertThat(ex.getErrors().getFirst()).contains("UTF-8"));
	}

	@Test
	void endpointImportsUploadedFileForTheAuthenticatedUser() throws Exception {
		var file = new MockMultipartFile("file", "extrato.csv", "text/csv",
				(HEADER + "2026-10-02,5000.00,Salário,INCOME\n").getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/transactions/import").file(file).header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalLines").value(1))
			.andExpect(jsonPath("$.imported").value(1))
			.andExpect(jsonPath("$.skippedDuplicates").value(0));

		assertThat(descriptionsOf(user)).containsExactly("Salário");
	}

	@Test
	void endpointReturnsProblemDetailForInvalidFileAndRequiresAuthentication() throws Exception {
		var invalid = new MockMultipartFile("file", "extrato.csv", "text/csv",
				(HEADER + "2026-10-02,abc,Salário,INCOME\n").getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/transactions/import").file(invalid).header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Arquivo inválido"))
			.andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("Linha 2:")));

		mockMvc.perform(multipart("/transactions/import").file(invalid)).andExpect(status().isUnauthorized());
	}

	private ImportSummaryResponse importCsv(String csv) {
		return importService.importCsv(stream(csv), user.getId());
	}

	private List<String> descriptionsOf(User owner) {
		return transactionService.list(owner.getId(), Pageable.unpaged())
			.map(TransactionResponse::description)
			.getContent();
	}

	private User newUser() {
		return userRepository.save(new User(UUID.randomUUID() + "@email.com", "hash-irrelevante", Role.USER));
	}

	private String bearer(User owner) {
		return "Bearer " + jwtService.generateToken(owner);
	}

	private static InputStream stream(String csv) {
		return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
	}

}

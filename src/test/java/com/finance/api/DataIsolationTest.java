package com.finance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.finance.api.security.JwtService;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Regra de ouro: um usuário nunca lê, altera ou apaga dados de outro (IDOR).
 * Recurso alheio responde 404, igual a um inexistente, para não confirmar que o id existe.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DataIsolationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private String alice;
	private String bob;
	private User aliceUser;
	private String aliceCategoryId;
	private String aliceTransactionId;

	@BeforeEach
	void setUp() throws Exception {
		aliceUser = newUser();
		alice = bearer(aliceUser);
		bob = bearer(newUser());

		aliceCategoryId = createAndGetId(alice, "/categories", """
				{"name": "Mercado"}
				""");
		aliceTransactionId = createAndGetId(alice, "/transactions", transactionJson("Compra do mês", aliceCategoryId));
	}

	// ---------------------------------------------------------------- transações

	@Test
	void cannotReadAnotherUsersTransaction() throws Exception {
		mockMvc.perform(as(bob, get("/transactions/{id}", aliceTransactionId))).andExpect(status().isNotFound());
	}

	@Test
	void cannotUpdateAnotherUsersTransaction() throws Exception {
		mockMvc.perform(as(bob, put("/transactions/{id}", aliceTransactionId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(transactionJson("Alterado pelo Bob", null))).andExpect(status().isNotFound());

		mockMvc.perform(as(alice, get("/transactions/{id}", aliceTransactionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("Compra do mês"))
			.andExpect(jsonPath("$.categoryId").value(aliceCategoryId));
	}

	@Test
	void cannotDeleteAnotherUsersTransaction() throws Exception {
		mockMvc.perform(as(bob, delete("/transactions/{id}", aliceTransactionId))).andExpect(status().isNotFound());

		mockMvc.perform(as(alice, get("/transactions/{id}", aliceTransactionId))).andExpect(status().isOk());
	}

	@Test
	void listingOnlyReturnsOwnTransactions() throws Exception {
		mockMvc.perform(as(bob, get("/transactions")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty())
			.andExpect(jsonPath("$.page.totalElements").value(0));

		mockMvc.perform(as(alice, get("/transactions")))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(aliceTransactionId));
	}

	@Test
	void userIdInPayloadIsIgnored() throws Exception {
		// Bob tenta criar uma transação "em nome" da Alice
		String body = """
				{"amount": 1.00, "date": "2026-10-01", "description": "Injetada", "type": "EXPENSE",
				 "userId": "%s"}
				""".formatted(aliceUser.getId());
		mockMvc.perform(as(bob, post("/transactions")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());

		mockMvc.perform(as(alice, get("/transactions"))).andExpect(jsonPath("$.page.totalElements").value(1));
		mockMvc.perform(as(bob, get("/transactions")))
			.andExpect(jsonPath("$.content[0].description").value("Injetada"));
	}

	// ---------------------------------------------------------------- categorias

	@Test
	void cannotReadAnotherUsersCategory() throws Exception {
		mockMvc.perform(as(bob, get("/categories/{id}", aliceCategoryId))).andExpect(status().isNotFound());
	}

	@Test
	void cannotRenameAnotherUsersCategory() throws Exception {
		mockMvc.perform(as(bob, put("/categories/{id}", aliceCategoryId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Hackeada"}
					""")).andExpect(status().isNotFound());

		mockMvc.perform(as(alice, get("/categories/{id}", aliceCategoryId)))
			.andExpect(jsonPath("$.name").value("Mercado"));
	}

	@Test
	void cannotDeleteAnotherUsersCategory() throws Exception {
		// 404 e não 409: responder "possui transações" confirmaria que a categoria existe
		mockMvc.perform(as(bob, delete("/categories/{id}", aliceCategoryId))).andExpect(status().isNotFound());

		mockMvc.perform(as(alice, get("/categories/{id}", aliceCategoryId))).andExpect(status().isOk());
	}

	@Test
	void listingOnlyReturnsOwnCategories() throws Exception {
		mockMvc.perform(as(bob, get("/categories"))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void sameCategoryNameIsAllowedForDifferentUsers() throws Exception {
		mockMvc.perform(as(bob, post("/categories")).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Mercado"}
				""")).andExpect(status().isCreated());
	}

	// ------------------------------------------------- referência cruzada

	@Test
	void cannotCreateTransactionInAnotherUsersCategory() throws Exception {
		mockMvc.perform(as(bob, post("/transactions"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(transactionJson("Na categoria da Alice", aliceCategoryId))).andExpect(status().isNotFound());

		mockMvc.perform(as(bob, get("/transactions"))).andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void cannotMoveOwnTransactionIntoAnotherUsersCategory() throws Exception {
		String bobTransactionId = createAndGetId(bob, "/transactions", transactionJson("Do Bob", null));

		mockMvc.perform(as(bob, put("/transactions/{id}", bobTransactionId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(transactionJson("Do Bob", aliceCategoryId))).andExpect(status().isNotFound());

		mockMvc.perform(as(bob, get("/transactions/{id}", bobTransactionId)))
			.andExpect(jsonPath("$.categoryId").doesNotExist());
	}

	// ---------------------------------------------------------- sem token

	@Test
	void requestsWithoutTokenAreRejected() throws Exception {
		mockMvc.perform(get("/transactions")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/categories")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/transactions/{id}", aliceTransactionId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer token-forjado")).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------- helpers

	private String createAndGetId(String token, String path, String json) throws Exception {
		String response = mockMvc.perform(as(token, post(path)).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private static String transactionJson(String description, String categoryId) {
		return """
				{"amount": 250.00, "date": "2026-10-01", "description": "%s", "type": "EXPENSE", "categoryId": %s}
				""".formatted(description, categoryId == null ? "null" : "\"" + categoryId + "\"");
	}

	private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, token);
	}

	private User newUser() {
		return userRepository.save(new User(UUID.randomUUID() + "@email.com", "hash-irrelevante", Role.USER));
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

}

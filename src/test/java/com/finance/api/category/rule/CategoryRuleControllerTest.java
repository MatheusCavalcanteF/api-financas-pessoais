package com.finance.api.category.rule;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.finance.api.TestcontainersConfiguration;
import com.finance.api.security.JwtService;
import com.finance.api.user.Role;
import com.finance.api.user.User;
import com.finance.api.user.UserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * CRUD de regras e isolamento entre usuários: regra ou categoria alheia responde
 * 404, igual a uma inexistente.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CategoryRuleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private String alice;
	private String bob;
	private String aliceCategoryId;
	private String aliceRuleId;

	@BeforeEach
	void setUp() throws Exception {
		alice = bearer(newUser());
		bob = bearer(newUser());
		aliceCategoryId = createAndGetId(alice, "/categories", """
				{"name": "Transporte"}
				""");
		aliceRuleId = createAndGetId(alice, "/category-rules", rule("Uber", aliceCategoryId));
	}

	// ------------------------------------------------------------------ CRUD

	@Test
	void crudLifecycle() throws Exception {
		perform(alice, get("/category-rules/{id}", aliceRuleId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.keyword").value("Uber"))
			.andExpect(jsonPath("$.categoryId").value(aliceCategoryId));

		perform(alice, put("/category-rules/{id}", aliceRuleId), rule("  99   Taxi ", aliceCategoryId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keyword").value("99 Taxi"));

		perform(alice, get("/category-rules")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].keyword").value("99 Taxi"));

		perform(alice, delete("/category-rules/{id}", aliceRuleId)).andExpect(status().isNoContent());
		perform(alice, get("/category-rules/{id}", aliceRuleId)).andExpect(status().isNotFound());
	}

	@Test
	void rejectsDuplicateKeywordIgnoringCaseAndAccents() throws Exception {
		perform(alice, post("/category-rules"), rule("UBER", aliceCategoryId)).andExpect(status().isConflict());

		createAndGetId(alice, "/category-rules", rule("Café", aliceCategoryId));
		perform(alice, post("/category-rules"), rule("cafe", aliceCategoryId)).andExpect(status().isConflict());
	}

	@Test
	void renamingARuleToItsOwnKeywordIsNotADuplicate() throws Exception {
		perform(alice, put("/category-rules/{id}", aliceRuleId), rule("uber", aliceCategoryId))
			.andExpect(status().isOk());
	}

	@Test
	void rejectsKeywordShorterThanThreeCharacters() throws Exception {
		perform(alice, post("/category-rules"), rule("  a b  ", aliceCategoryId)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.keyword").exists());
	}

	@Test
	void deletingTheCategoryDeletesItsRules() throws Exception {
		perform(alice, delete("/categories/{id}", aliceCategoryId)).andExpect(status().isNoContent());

		perform(alice, get("/category-rules/{id}", aliceRuleId)).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ IDOR

	@Test
	void cannotReadAnotherUsersRule() throws Exception {
		perform(bob, get("/category-rules/{id}", aliceRuleId)).andExpect(status().isNotFound());
	}

	@Test
	void cannotUpdateAnotherUsersRule() throws Exception {
		String bobCategoryId = createAndGetId(bob, "/categories", """
				{"name": "Do Bob"}
				""");

		perform(bob, put("/category-rules/{id}", aliceRuleId), rule("Sequestrada", bobCategoryId))
			.andExpect(status().isNotFound());

		perform(alice, get("/category-rules/{id}", aliceRuleId)).andExpect(jsonPath("$.keyword").value("Uber"))
			.andExpect(jsonPath("$.categoryId").value(aliceCategoryId));
	}

	@Test
	void cannotDeleteAnotherUsersRule() throws Exception {
		perform(bob, delete("/category-rules/{id}", aliceRuleId)).andExpect(status().isNotFound());

		perform(alice, get("/category-rules/{id}", aliceRuleId)).andExpect(status().isOk());
	}

	@Test
	void listingOnlyReturnsOwnRules() throws Exception {
		perform(bob, get("/category-rules")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty())
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void cannotPointARuleToAnotherUsersCategory() throws Exception {
		perform(bob, post("/category-rules"), rule("Mercado", aliceCategoryId)).andExpect(status().isNotFound());

		String bobCategoryId = createAndGetId(bob, "/categories", """
				{"name": "Mercado"}
				""");
		String bobRuleId = createAndGetId(bob, "/category-rules", rule("Mercado", bobCategoryId));
		perform(bob, put("/category-rules/{id}", bobRuleId), rule("Mercado", aliceCategoryId))
			.andExpect(status().isNotFound());
	}

	@Test
	void sameKeywordIsAllowedForDifferentUsers() throws Exception {
		String bobCategoryId = createAndGetId(bob, "/categories", """
				{"name": "Transporte"}
				""");

		perform(bob, post("/category-rules"), rule("Uber", bobCategoryId)).andExpect(status().isCreated());
	}

	@Test
	void requestsWithoutTokenAreRejected() throws Exception {
		mockMvc.perform(get("/category-rules")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/category-rules/{id}", aliceRuleId)).andExpect(status().isUnauthorized());
	}

	// --------------------------------------------------------------- helpers

	private ResultActions perform(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
	}

	private ResultActions perform(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
		return perform(token, request.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String createAndGetId(String token, String path, String json) throws Exception {
		String response = perform(token, post(path), json).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private static String rule(String keyword, String categoryId) {
		return """
				{"keyword": "%s", "categoryId": "%s"}
				""".formatted(keyword, categoryId);
	}

	private User newUser() {
		return userRepository.save(new User(UUID.randomUUID() + "@email.com", "hash-irrelevante", Role.USER));
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

}

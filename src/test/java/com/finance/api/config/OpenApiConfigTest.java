package com.finance.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.finance.api.TestcontainersConfiguration;

/**
 * O Swagger UI decide o cadeado e o envio do header pelo documento OpenAPI, então
 * é ele que se verifica.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void documentsJwtBearerAndRequiresItEverywhereExceptAuth() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
			// Exigência global, herdada pelas rotas que não a sobrescrevem
			.andExpect(jsonPath("$.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/reports/monthly'].get.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/category-rules/apply-retroactive'].post.security").doesNotExist())
			// Lista vazia anula a global: login e cadastro sem cadeado
			.andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/auth/register'].post.security").isEmpty());
	}

}

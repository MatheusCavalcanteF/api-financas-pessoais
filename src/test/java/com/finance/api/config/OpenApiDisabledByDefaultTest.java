package com.finance.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.finance.api.TestcontainersConfiguration;

/**
 * Sem SWAGGER_ENABLED, o springdoc não sobe e as rotas dele não existem. Espera-se
 * 404, e não só "diferente de 200": ligado, /swagger-ui.html responde 302.
 * O .env da raiz não interfere: o application.properties de teste desvia o ENV_FILE.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDisabledByDefaultTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsIsNotExposed() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUiIsNotExposed() throws Exception {
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
	}

}

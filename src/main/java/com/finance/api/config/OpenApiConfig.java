package com.finance.api.config;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Documenta a autenticação JWT para o botão "Authorize" do Swagger UI: o token
 * de {@code POST /auth/login} vale para todas as rotas, menos as de {@code /auth}.
 */
@Configuration
@OpenAPIDefinition(
		info = @Info(title = "API de Finanças Pessoais", version = "v1"),
		security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
		bearerFormat = "JWT")
public class OpenApiConfig {

	static final String BEARER_AUTH = "bearerAuth";

	// Espelha o permitAll do SecurityConfig: se uma rota pública nova surgir lá, ajuste aqui
	private static final String PUBLIC_PREFIX = "/auth/";

	/**
	 * A exigência global vem do {@code @OpenAPIDefinition}; aqui ela é retirada das
	 * rotas públicas. Lista vazia na operação sobrescreve a global, então o Swagger
	 * não mostra cadeado nem envia o header nelas.
	 */
	@Bean
	OpenApiCustomizer publicAuthEndpoints() {
		return openApi -> openApi.getPaths()
			.entrySet()
			.stream()
			.filter(path -> path.getKey().startsWith(PUBLIC_PREFIX))
			.flatMap(path -> path.getValue().readOperations().stream())
			.forEach(operation -> operation.setSecurity(List.of()));
	}

}

package ru.itmo.nemat.weezzy.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class OpenApiConfig {
	private static final String BEARER_AUTH = "bearerAuth";
	private static final Set<String> PUBLIC_PATHS = Set.of(
			"/api/auth/register",
			"/api/auth/login",
			"/api/auth/refresh",
			"/api/auth/email/verify",
			"/api/auth/email/resend",
			"/api/auth/password/forgot",
			"/api/auth/password/reset"
	);

	@Bean
	public OpenAPI weezzyOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Weezzy API")
						.version("v1")
						.description("API сервиса нетворкинга и поиска команды в ИТМО"))
				.components(new Components()
						.addSecuritySchemes(
								BEARER_AUTH,
								new SecurityScheme()
										.type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT")
						));
	}

	@Bean
	public OpenApiCustomizer protectedOperationsCustomizer() {
		return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
			if (!PUBLIC_PATHS.contains(path)) {
				pathItem.readOperations().forEach(operation ->
						operation.addSecurityItem(
								new SecurityRequirement().addList(BEARER_AUTH)
						)
				);
			}
		});
	}
}

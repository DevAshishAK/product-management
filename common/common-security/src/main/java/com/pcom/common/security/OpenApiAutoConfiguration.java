package com.pcom.common.security;

import com.pcom.common.core.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OpenApiCustomizer;

/** OpenAPI 3 metadata: bearer-JWT security scheme and the standard error responses on every operation. */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiAutoConfiguration {

    @Bean
    OpenAPI pcomOpenApi(@Value("${spring.application.name:service}") String name) {
        return new OpenAPI()
                .info(new Info().title(name + " API").version("v1")
                        .description("Production Catalog & Order Management Platform"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            ModelConverters.getInstance().read(ApiError.class).forEach(openApi.getComponents()::addSchemas);
            ModelConverters.getInstance().read(ApiError.FieldViolation.class).forEach(openApi.getComponents()::addSchemas);
            Content content = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")));
            if (openApi.getPaths() == null) return;
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                op.getResponses().addApiResponse("400", new ApiResponse().description("Validation error").content(content));
                op.getResponses().addApiResponse("401", new ApiResponse().description("Missing or invalid JWT").content(content));
                op.getResponses().addApiResponse("403", new ApiResponse().description("Forbidden").content(content));
                op.getResponses().addApiResponse("500", new ApiResponse().description("Unexpected error").content(content));
            }));
        };
    }
}

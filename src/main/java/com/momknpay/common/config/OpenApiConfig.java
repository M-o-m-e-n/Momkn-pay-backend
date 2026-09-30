package com.momknpay.common.config;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.momknpay.common.error.ErrorResponse;
import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.Headers;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * Makes the generated spec at {@code /v3/api-docs} (Swagger UI at {@code /docs}) match the frozen
 * contract in {@code docs/openapi.yaml}: the common headers on every operation, {@code X-User-Id}
 * where a controller takes {@code @CurrentUser}, and the error envelope on every error response.
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR_SCHEMA = "ErrorResponse";

    static {
        // the @CurrentUser parameter is resolved from a header, not a query parameter
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI momknPayOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Momkn Pay API")
                                .version("1.0.0")
                                .description(
                                        "Simulated bill-payment API. No authentication: the user"
                                                + " is named by X-User-Id (ADR-001). Money is"
                                                + " integer piastres; timestamps are ISO 8601"
                                                + " UTC. Frozen contract: docs/openapi.yaml."))
                .servers(List.of(new Server().url("https://api.momknpay.local")))
                .components(new Components().schemas(errorSchemas()));
    }

    /** Common headers everywhere; X-User-Id where the handler resolves the current user. */
    @Bean
    OperationCustomizer contractHeaders() {
        return (Operation operation, HandlerMethod handler) -> {
            operation.addParametersItem(
                    header(Headers.REQUEST_ID, "Client-generated UUID, echoed back.")
                            .schema(new StringSchema().format("uuid")));
            operation.addParametersItem(
                    header(Headers.CLIENT_PLATFORM, "Calling app.")
                            .schema(new StringSchema()._enum(List.of("ios", "android"))));
            operation.addParametersItem(
                    header(Headers.CLIENT_VERSION, "App version, 1–32 characters.")
                            .schema(new StringSchema().minLength(1).maxLength(32)));
            boolean needsUser =
                    Arrays.stream(handler.getMethodParameters())
                            .anyMatch(p -> p.hasParameterAnnotation(CurrentUser.class));
            if (needsUser) {
                operation.addParametersItem(
                        header(Headers.USER_ID, "User this request acts for (trusted, no auth).")
                                .schema(new StringSchema().maxLength(32)));
            }
            return operation;
        };
    }

    /** Every declared non-2xx response carries the error envelope. */
    @Bean
    OpenApiCustomizer errorEnvelopeOnErrors() {
        return openApi -> {
            for (PathItem path : openApi.getPaths().values()) {
                for (Operation operation : path.readOperations()) {
                    operation.getResponses().forEach(OpenApiConfig::attachEnvelope);
                }
            }
        };
    }

    private static void attachEnvelope(String status, ApiResponse response) {
        if (!status.startsWith("2") && response.getContent() == null) {
            Schema<?> envelope = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
            response.content(
                    new Content()
                            .addMediaType(
                                    APPLICATION_JSON_VALUE, new MediaType().schema(envelope)));
        }
    }

    private static Parameter header(String name, String description) {
        return new HeaderParameter().name(name).required(true).description(description);
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Schema> errorSchemas() {
        return ModelConverters.getInstance().readAll(ErrorResponse.class);
    }
}

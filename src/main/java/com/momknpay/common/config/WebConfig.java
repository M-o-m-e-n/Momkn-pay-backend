package com.momknpay.common.config;

import com.momknpay.common.web.RequiredHeadersInterceptor;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Adds the {@code /v1} prefix to our controllers (Swagger, actuator and /error stay at the root)
 * and registers the interceptors.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/v1";

    private final RequiredHeadersInterceptor requiredHeaders;

    public WebConfig(RequiredHeadersInterceptor requiredHeaders) {
        this.requiredHeaders = requiredHeaders;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                API_PREFIX,
                HandlerTypePredicate.forBasePackage("com.momknpay")
                        .and(type -> !ErrorController.class.isAssignableFrom(type)));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiredHeaders).addPathPatterns(API_PREFIX + "/**");
    }
}

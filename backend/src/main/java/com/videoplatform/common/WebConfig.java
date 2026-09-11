package com.videoplatform.common;

import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.organization.OrganizationContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS para desenvolvimento / tuneis + registro do argument resolver que injeta
 * {@link OrganizationContext} nos controllers (Sprint 7 §26).
 *
 * <p>Por padrao o CORS libera <b>qualquer origem</b> ({@code app.cors.allowed-origins=*}).
 * A API nao usa cookies (o token vai no header {@code Authorization}), entao
 * {@code allowCredentials} fica desligado e o curinga e' seguro.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;
    private final ObjectProvider<OrganizationContextResolver> orgContextResolver;

    public WebConfig(@Value("${app.cors.allowed-origins:*}") String origins,
                     ObjectProvider<OrganizationContextResolver> orgContextResolver) {
        this.allowedOriginPatterns = List.of(origins.split(","));
        this.orgContextResolver = orgContextResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        OrganizationContextResolver resolver = orgContextResolver.getIfAvailable();
        if (resolver == null) {
            return; // fatia @WebMvcTest — o stub de teste registra o seu proprio
        }
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(OrganizationContext.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return resolver.resolve();
            }
        });
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Correlation-ID"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

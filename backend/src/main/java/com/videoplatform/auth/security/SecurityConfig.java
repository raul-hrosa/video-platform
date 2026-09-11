package com.videoplatform.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Duas cadeias de seguranca:
 *
 * <ul>
 *   <li>webhook do LiveKit — totalmente aberta (validacao propria via assinatura
 *       LiveKit, nunca JWT de usuario); fica fora do filtro de bearer token para
 *       nao rejeitar um header Authorization "cru" (o LiveKit envia o JWT sem o
 *       prefixo {@code Bearer}).</li>
 *   <li>demais rotas — stateless JWT resource server; publico apenas cadastro,
 *       login e health.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/webhooks/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/v1/auth/register", "POST"),
                                new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
                                // entrada de visitante sem conta pelo link da sala
                                new AntPathRequestMatcher("/api/v1/rooms/*/guest-token", "POST"),
                                // nomes de exibicao dos participantes da sala (Sprint 11):
                                // o provider de midia nao propaga nome, entao o cliente
                                // na chamada resolve por aqui (rota de leitura, sem dados sensiveis)
                                new AntPathRequestMatcher("/api/v1/rooms/*/participant-names", "GET"),
                                // qualidade ao vivo calculada pela Quality Engine do provider
                                // de midia (Sprint 12): rota de leitura, so verdito + metricas
                                new AntPathRequestMatcher("/api/v1/rooms/*/media-quality", "GET"),
                                // link publico de Appointment (Sprint 8 §32): so participacao
                                new AntPathRequestMatcher("/api/v1/public/**"),
                                new AntPathRequestMatcher("/actuator/health"),
                                new AntPathRequestMatcher("/actuator/health/**"))
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new AuthenticatedUserJwtConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}

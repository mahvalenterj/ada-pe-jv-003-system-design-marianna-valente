package com.ada.checkout.config;

import com.ada.checkout.web.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;

/**
 * Hardening central do resource server (Zero Trust).
 *
 * <p>Princípios aplicados:
 * <ul>
 *   <li><b>Zero Trust / stateless:</b> cada requisição é autenticada e autorizada
 *       isoladamente a partir do JWT; não há sessão e não se confia na rede interna.</li>
 *   <li><b>Menor privilégio:</b> autorização por <i>scopes</i> finos
 *       ({@code SCOPE_orders.read}, {@code SCOPE_orders.write}) via {@code @PreAuthorize}.</li>
 *   <li><b>Fail-secure default:</b> {@code anyRequest().authenticated()} — nada fica
 *       aberto por omissão; endpoints públicos são a exceção explícita.</li>
 *   <li><b>A05 - Security Misconfiguration:</b> CSP, X-Frame-Options e HSTS ligados;
 *       CORS restrito a origens confiáveis.</li>
 * </ul>
 *
 * <p><b>Validação do token:</b> em produção use o IdP (Keycloak) via
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}. Para o laboratório
 * local (sem Keycloak) validamos a assinatura HS256 com um segredo configurável — o
 * suficiente para o exercício de <i>JWT tampering</i> do RedTeam: tokens adulterados
 * falham na verificação de assinatura.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final String hmacSecret;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${app.jwt.hmac-secret:change-me-dev-only-0123456789-0123456789-0123456789}") String hmacSecret,
            @Value("${app.cors.allowed-origins:https://app.checkout.example.com}") List<String> allowedOrigins) {
        this.hmacSecret = hmacSecret;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RateLimitInterceptor rateLimitInterceptor) throws Exception {
        http
            // API puramente baseada em token (Bearer): sem cookies de sessão, CSRF não se aplica.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000))
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'none'; frame-ancestors 'none'"))
            );

        // Rate limit (Bucket4j) aplicado via filtro registrado no RateLimitInterceptor.
        http.addFilterBefore(rateLimitInterceptor,
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Decoder HS256 para o laboratório local. Em produção, remova este bean e configure
     * {@code jwk-set-uri} apontando para o Keycloak (validação de assinatura RS256 + rotação de chaves).
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(hmacSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);          // allowlist — nunca "*"
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

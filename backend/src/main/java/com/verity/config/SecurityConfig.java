package com.verity.config;

import com.verity.auth.ClerkJwtAuthenticationConverter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer-token security. Clerk issues session tokens; this application only verifies
 * them, as an OAuth2 resource server. There are no server sessions and no credentials here.
 * Privileged routes are additionally gated by {@code @PreAuthorize} (enabled by
 * {@link EnableMethodSecurity}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ClerkJwtAuthenticationConverter clerkJwtAuthenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // Stateless API: no session, no CSRF token. This app holds no cookie of its own —
                // Clerk's session cookie lives on the frontend origin, and every route here
                // authenticates from a non-ambient bearer header, which CSRF cannot forge.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/analysis").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/analysis/*").permitAll()
                        // Transparency pages are public: model metrics, trends, and campaign browsing.
                        .requestMatchers(HttpMethod.GET, "/api/v1/models/active/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/trends").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/campaigns", "/api/v1/campaigns/*").permitAll()
                        // Every admin route is ADMIN-only. Enforced here at the URL layer as defense
                        // in depth on top of the per-method @PreAuthorize, so a route that ever
                        // forgets the annotation still cannot be reached by a non-admin.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(clerkJwtAuthenticationConverter)));
        return http.build();
    }

    /**
     * Verifies Clerk's session tokens (RS256) against its published JWKS.
     *
     * <p>Built from the JWKS URI rather than the issuer location, because
     * {@code withIssuerLocation(...).build()} fetches the OIDC discovery document <em>eagerly</em>,
     * inside {@code build()}. That would make startup depend on Clerk being reachable — a bad trade
     * for an app that cold-starts on a free tier that sleeps, and one that would take the whole
     * context down (including tests) on a transient network failure. {@code withJwkSetUri} defers
     * the fetch to the first token, then caches the keys and follows rotation.
     *
     * <p>The trade is that no discovery document is read, so nothing tells us the issuer — hence the
     * explicit issuer validator. It matters: signature alone only proves <em>some</em> Clerk instance
     * minted the token. The {@code iss} check is what makes a token from anyone else's Clerk useless
     * here.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.clerk.issuer}") String issuer,
            @Value("${app.clerk.jwks-uri:}") String jwksUri) {
        // An absent CLERK_ISSUER is caught by the placeholder, but one set to an empty string still
        // resolves. Blank would build a nonsense JWKS URL and an issuer validator matching "", so
        // every token fails while /actuator/health stays green — fail the deploy instead.
        Assert.hasText(issuer, "CLERK_ISSUER must be set; no token can be verified without it");
        String keys = jwksUri.isBlank() ? issuer + "/.well-known/jwks.json" : jwksUri;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true); // the refresh cookie must be allowed on cross-origin calls
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

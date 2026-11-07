package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration for the application.
 *
 * <p>Defines the security filter chain with these rules:</p>
 * <ul>
 *   <li>CSRF is disabled because the API is stateless and uses JWT authentication.</li>
 *   <li>Sessions are disabled with {@link SessionCreationPolicy#STATELESS}.</li>
 *   <li>Public routes are {@code /auth/**} (login and registration) and
 *       {@code /products/**} (the catalog).</li>
 *   <li>All other routes require a valid JWT.</li>
 *   <li>{@link JwtAuthenticationFilter} runs before Spring Security's standard
 *       filter to authenticate the request from its token.</li>
 *   <li>Allowed CORS origins come from {@code app.cors.allowed-origins}, configured
 *       by profile (dev / prod).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    /**
     * Builds the HTTP security filter chain.
     *
     * <p>The resulting filter order is
     * {@code JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ...rest of the chain}.</p>
     *
     * @param http the Spring HTTP security configuration builder
     * @param corsConfigurationSource CORS configuration for the active profile
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if Spring Security fails to build the chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )//Da 401 si no me dan el Token
                .authorizeHttpRequests(authRequest -> authRequest
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/products/**").permitAll()
                        .requestMatchers("/health/**").permitAll()
                        .requestMatchers("/admin/**").hasAuthority("ADMIN") //Podria ponerlo en el EndPoint pero sera mejor aqui
                        .anyRequest().authenticated()
                )
                .sessionManagement(sessionManager ->
                        sessionManager.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Configures CORS using {@code app.cors.allowed-origins}, a comma-separated
     * list defined in {@code application-dev} or {@code application-prod}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
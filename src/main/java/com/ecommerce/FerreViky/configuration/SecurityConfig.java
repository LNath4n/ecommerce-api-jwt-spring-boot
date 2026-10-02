package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración central de Spring Security para la aplicación.
 * <p>
 * Define la cadena de filtros de seguridad con las siguientes reglas:
 * <ul>
 *   <li>CSRF deshabilitado — la API es stateless y usa JWT, por lo que no aplica protección CSRF.</li>
 *   <li>Sesiones deshabilitadas — {@link SessionCreationPolicy#STATELESS} para que Spring Security
 *       no cree ni use sesiones HTTP.</li>
 *   <li>Rutas públicas: {@code /auth/**} (login y registro) y {@code /productos/**} (catálogo).</li>
 *   <li>Cualquier otra ruta requiere autenticación JWT válida.</li>
 *   <li>El {@link JwtAuthenticationFilter} se ejecuta antes del filtro estándar de Spring Security
 *       para inyectar la autenticación desde el token antes de que se evalúen las reglas de acceso.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    /**
     * Define y construye la cadena de filtros de seguridad HTTP.
     * <p>
     * El orden de filtros resultante es:
     * {@code JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ...resto de la cadena}
     *
     * @param http builder de configuración de seguridad HTTP de Spring
     * @return {@link SecurityFilterChain} configurada y lista para registrarse en el contexto
     * @throws Exception si la configuración de Spring Security falla al construir la cadena
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authRequest -> authRequest
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/productos/**").permitAll()
                        .requestMatchers("/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sessionManager ->
                        sessionManager.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

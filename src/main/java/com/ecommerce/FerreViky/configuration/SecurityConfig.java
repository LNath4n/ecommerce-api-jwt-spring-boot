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
 *   <li>Los orígenes CORS permitidos se leen de {@code app.cors.allowed-origins}, definido por perfil
 *       (dev / prod).</li>
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
     * @param http                    builder de configuración de seguridad HTTP de Spring
     * @param corsConfigurationSource configuración CORS según el perfil activo
     * @return {@link SecurityFilterChain} configurada y lista para registrarse en el contexto
     * @throws Exception si la configuración de Spring Security falla al construir la cadena
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
                        .requestMatchers("/productos/**").permitAll()
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
     * Configuración CORS. Los orígenes permitidos vienen de {@code app.cors.allowed-origins}
     * (separados por coma si son varios), definido en application-dev / application-prod.
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
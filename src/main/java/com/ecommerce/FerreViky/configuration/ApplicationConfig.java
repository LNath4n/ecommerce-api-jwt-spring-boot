package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración de los beans de autenticación usados por Spring Security.
 * <p>
 * Esta clase define toda la infraestructura necesaria para autenticar clientes
 * contra la base de datos: cómo se cargan los usuarios ({@link UserDetailsService}),
 * cómo se verifican sus contraseñas ({@link PasswordEncoder}) y cómo se combinan
 * ambos en un {@link AuthenticationProvider}. Estos beans son consumidos por
 * {@code SecurityConfig} para armar la cadena de filtros de seguridad, y por los
 * servicios de login que necesiten autenticar credenciales manualmente.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final ClienteRepository clienteRepository;

    /**
     * Expone el {@link AuthenticationManager} por defecto de Spring Security.
     * <p>
     * Se delega en {@link AuthenticationConfiguration} para reutilizar el manager
     * que Spring construye automáticamente a partir de los beans de autenticación
     * ya definidos en este mismo archivo (como {@link #authenticationProvider()}).
     * Este bean es el que se inyecta en servicios de login para autenticar
     * credenciales con {@code authenticationManager.authenticate(...)}.
     *
     * @param config configuración de autenticación gestionada internamente por Spring Security
     * @return el {@link AuthenticationManager} listo para autenticar credenciales
     * @throws Exception si Spring Security falla al construir el manager
     */
    @Bean
    public AuthenticationManager authenticationManager (AuthenticationConfiguration config) throws Exception{
        return config.getAuthenticationManager();
    }

    /**
     * Define el proveedor de autenticación basado en acceso a base de datos (DAO).
     * <p>
     * Conecta el {@link UserDetailsService} (que carga el {@code Cliente} por email)
     * con el {@link PasswordEncoder} (que verifica la contraseña con BCrypt). Es este
     * provider el que Spring Security usa para validar las credenciales durante el
     * login.
     *
     * @return {@link AuthenticationProvider} configurado con el {@link UserDetailsService}
     *         y el {@link PasswordEncoder} de la aplicación
     */
    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Codificador de contraseñas usado tanto al registrar clientes como al
     * verificar sus credenciales en el login.
     * <p>
     * Usa BCrypt, que aplica un salt aleatorio distinto por contraseña y es el
     * algoritmo recomendado por Spring Security para el hashing de contraseñas.
     *
     * @return instancia de {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Define cómo Spring Security carga los datos de un usuario a partir de su
     * username.
     * <p>
     * En esta aplicación el "username" es en realidad el email del cliente. Se
     * busca el {@code Cliente} correspondiente en {@link ClienteRepository} y, si
     * no existe ninguno con ese email, se lanza {@link UsernameNotFoundException}
     * para que Spring Security rechace la autenticación.
     *
     * @return {@link UserDetailsService} que resuelve un {@link UserDetails}
     *         (el {@code Cliente}) a partir del email
     */
    @Bean
    public UserDetailsService userDetailsService(){
        return email -> clienteRepository.findByEmail(email).orElseThrow(()-> new UsernameNotFoundException("Not Found"));
    }

}
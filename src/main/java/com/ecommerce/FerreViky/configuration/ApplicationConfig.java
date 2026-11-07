package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.repository.CustomerRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration for the authentication beans used by Spring Security.
 *
 * <p>This class defines the infrastructure required to authenticate customers
 * against the database: loading users through {@link UserDetailsService},
 * verifying passwords with {@link PasswordEncoder}, and combining both in an
 * {@link AuthenticationProvider}. These beans are used by {@code SecurityConfig}
 * to build the security filter chain and by login services that authenticate
 * credentials manually.</p>
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final CustomerRepository customerRepository;

    /**
     * Exposes Spring Security's default {@link AuthenticationManager}.
     *
     * <p>Delegates to {@link AuthenticationConfiguration} to reuse the manager
     * Spring builds from the authentication beans defined in this class, such
     * as {@link #authenticationProvider()}. Login services use this bean to
     * authenticate credentials with {@code authenticationManager.authenticate(...)}.</p>
     *
     * @param config authentication configuration managed by Spring Security
     * @return the {@link AuthenticationManager} ready to authenticate credentials
     * @throws Exception if Spring Security fails to build the manager
     */
    @Bean
    public AuthenticationManager authenticationManager (AuthenticationConfiguration config) throws Exception{
        return config.getAuthenticationManager();
    }

    /**
     * Defines the database-backed authentication provider (DAO).
     *
     * <p>Connects the {@link UserDetailsService}, which loads a {@code Customer}
     * by email, with the {@link PasswordEncoder}, which verifies the password
     * with BCrypt. Spring Security uses this provider to validate login credentials.</p>
     *
     * @return an {@link AuthenticationProvider} configured with the application's
     *         {@link UserDetailsService} and {@link PasswordEncoder}
     */
    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Encodes passwords during customer registration and credential verification.
     *
     * <p>BCrypt applies a unique random salt to each password and is Spring
     * Security's recommended password-hashing algorithm.</p>
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines how Spring Security loads a user from their username.
     *
     * <p>In this application, the username is the customer's email. The matching
     * {@code Customer} is loaded through {@link CustomerRepository}; if no
     * customer exists for that email, {@link UsernameNotFoundException} is
     * thrown so Spring Security can reject authentication.</p>
     *
     * @return a {@link UserDetailsService} that resolves a {@link UserDetails}
     *         ({@code Customer}) from an email address
     */
    @Bean
    public UserDetailsService userDetailsService(){
        return email -> customerRepository.findByEmail(email).orElseThrow(()-> new UsernameNotFoundException("Not Found"));
    }

}
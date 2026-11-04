package com.ecommerce.FerreViky.config;

import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.Rol;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String email = "admin@admin.com";

        if (clienteRepository.existsByEmail(email)) return;

        clienteRepository.save(Cliente.builder()
                .email(email)
                .password(passwordEncoder.encode("1234567890"))
                .rol(Rol.ADMIN)
                .nombre("Admin")
                .build());
    }
}
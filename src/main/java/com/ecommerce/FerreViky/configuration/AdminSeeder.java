package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String email = "admin@admin.com";

        if (customerRepository.existsByEmail(email)) return;

        customerRepository.save(Customer.builder()
                .email(email)
                .password(passwordEncoder.encode("1234567890"))
                .role(Customer.Role.ADMIN)
                .name("Admin")
                .build());
    }
}
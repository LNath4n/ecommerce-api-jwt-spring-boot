package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Customer.Role;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.models.Product.ProductType;
import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.repository.CustomerRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.utils.CodeHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final VirtualCodeRepository virtualCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final CodeHasher codeHasher;

    @Override
    public void run(String... args) {
        String email = "admin@admin.com";

        if (!customerRepository.existsByEmail(email)) {
            customerRepository.save(Customer.builder()
                    .email(email)
                    .password(passwordEncoder.encode("1234567890"))
                    .role(Role.ADMIN)
                    .name("Admin")
                    .build());
        }

        Product product = productRepository.findBySku("DEMO-PRODUCT-001")
                .orElseGet(() -> {
                    Product demoProduct = new Product();
                    demoProduct.setSku("DEMO-PRODUCT-001");
                    demoProduct.setName("Producto de prueba");
                    demoProduct.setSlug("producto-de-prueba");
                    demoProduct.setDescription("Producto de ejemplo para probar compras y códigos.");
                    demoProduct.setPlatform("Demo");
                    demoProduct.setType(ProductType.SOFTWARE);
                    demoProduct.setRegion("GLOBAL");
                    demoProduct.setCurrency("MXN");
                    demoProduct.setSalePrice(new BigDecimal("49.99"));
                    demoProduct.setCostPrice(new BigDecimal("25.00"));
                    demoProduct.setRedemptionInstructions("Ve a Steam");
                    demoProduct.setActive(true);
                    return productRepository.save(demoProduct);
                });

        List<String> codes = List.of(
                "DEMO-PRODUCT-CODE-001",
                "DEMO-PRODUCT-CODE-002",
                "DEMO-PRODUCT-CODE-003");

        // hash -> code
        Map<String, String> codesByHash = new LinkedHashMap<>();
        for (String code : codes) {
            codesByHash.putIfAbsent(codeHasher.hash(code), code);
        }

        Set<String> existingHashes = new HashSet<>(
                virtualCodeRepository.findExistingHashes(codesByHash.keySet()));

        List<VirtualCode> codesToSave = codesByHash.entrySet().stream()
                .filter(entry -> !existingHashes.contains(entry.getKey()))
                .map(entry -> {
                    VirtualCode virtualCode = new VirtualCode();
                    virtualCode.setProduct(product);
                    virtualCode.setCode(entry.getValue());
                    virtualCode.setCodeHash(entry.getKey());
                    return virtualCode;
                })
                .toList();

        if (!codesToSave.isEmpty()) {
            virtualCodeRepository.saveAll(codesToSave);
        }
    }
}

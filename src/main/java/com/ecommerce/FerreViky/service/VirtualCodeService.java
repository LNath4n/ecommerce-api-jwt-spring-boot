package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.virtualcode.VirtualCodeExceptions.DuplicateCodesException;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.ProductNotFoundException;
import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
import com.ecommerce.FerreViky.utils.CodeHasher;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@AllArgsConstructor
public class VirtualCodeService {
    private final VirtualCodeRepository virtualCodeRepository;
    private final ProductRepository productRepository;
    private final CodeHasher codeHasher;
    /**
     * Adds new codes to a product. They are created with the {@code AVAILABLE} status.
     *
     * @return the number of codes saved
     */
    @Transactional
    public int addCodesToProduct(Long productId, List<String> codes) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        List<String> normalizedCodes = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();

        if (normalizedCodes.isEmpty()) {
            throw new IllegalArgumentException("No valid codes were provided.");
        }

        // hash -> code (el hash es la identidad del código)
        Map<String, String> codesByHash = new LinkedHashMap<>();
        for (String code : normalizedCodes) {
            codesByHash.putIfAbsent(codeHasher.hash(code), code);
        }

        List<String> existingHashes = virtualCodeRepository.findExistingHashes(codesByHash.keySet());
        if (!existingHashes.isEmpty()) {
            throw new DuplicateCodesException(existingHashes.size());
        }

        List<VirtualCode> newCodes = codesByHash.entrySet().stream().map(entry -> {
            VirtualCode virtualCode = new VirtualCode();
            virtualCode.setProduct(product);
            virtualCode.setCode(entry.getValue());
            virtualCode.setCodeHash(entry.getKey());
            return virtualCode;
        }).toList();

        try {
            virtualCodeRepository.saveAllAndFlush(newCodes);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCodesException(0); // Race condition / Carrera
        }

        return newCodes.size();
    }

    /** Saves a single code for a product. */
    @Transactional
    public int addCodeToProduct(Long productId, String code) {
        return addCodesToProduct(productId, Collections.singletonList(code));
    }
}
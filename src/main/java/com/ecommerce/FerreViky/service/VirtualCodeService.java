package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.codigoVirtual.CodigosVirtualesExceptions.CodigosDuplicadosException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
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

    /**
     * Adds new codes to a product. They are created with the {@code AVAILABLE} status.
     *
     * @return the number of codes saved
     */
    @Transactional
    public int addCodesToProduct(Long productId, List<String> codes) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductoNoEncontradoException(productId));

        List<String> normalizedCodes = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();

        if (normalizedCodes.isEmpty()) {
            throw new IllegalArgumentException("No valid codes were provided.");
        }

        List<String> existingCodes = virtualCodeRepository.findExistingCodes(normalizedCodes);
        if (!existingCodes.isEmpty()) {
            throw new CodigosDuplicadosException(existingCodes.size());
        }

        List<VirtualCode> newCodes = normalizedCodes.stream().map(code -> {
            VirtualCode virtualCode = new VirtualCode();
            virtualCode.setProduct(product);
            virtualCode.setCode(code);
            return virtualCode;
        }).toList();

        try {
            virtualCodeRepository.saveAllAndFlush(newCodes);
        } catch (DataIntegrityViolationException e) {
            throw new CodigosDuplicadosException(0); // Race condition / Carrera
        }

        return newCodes.size();
    }

    /** Saves a single code for a product. */
    @Transactional
    public int addCodeToProduct(Long productId, String code) {
        return addCodesToProduct(productId, Collections.singletonList(code));
    }
}
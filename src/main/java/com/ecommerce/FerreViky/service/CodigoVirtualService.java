package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.codigoVirtual.CodigosVirtualesExceptions.CodigosDuplicadosException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.models.CodigoVirtual;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@AllArgsConstructor
public class CodigoVirtualService {
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final ProductoRepository productoRepository;

    /**
     * Carga códigos nuevos para un producto. Nacen DISPONIBLE.
     * @return cuántos códigos se guardaron
     */
    @Transactional
    public int guardarCodigos(Long productoId, List<String> codigos) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException(productoId));

        List<String> limpios = codigos.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .distinct()
                .toList();

        if (limpios.isEmpty()) {
            throw new IllegalArgumentException("No se recibió ningún código válido");
        }

        List<String> existentes = codigoVirtualRepository.findCodigosExistentes(limpios);
        if (!existentes.isEmpty()) {
            throw new CodigosDuplicadosException(existentes.size());
        }

        List<CodigoVirtual> nuevos = limpios.stream().map(texto -> {
            CodigoVirtual cv = new CodigoVirtual();
            cv.setProducto(producto);
            cv.setCodigo(texto);
            return cv;
        }).toList();

        try {
            codigoVirtualRepository.saveAllAndFlush(nuevos);
        } catch (DataIntegrityViolationException e) {
            throw new CodigosDuplicadosException(0); // carrera
        }

        return nuevos.size();
    }

    /** Atajo para un solo código. */
    @Transactional
    public int guardarCodigo(Long productoId, String codigo) {
        return guardarCodigos(productoId, Collections.singletonList(codigo));
    }
}
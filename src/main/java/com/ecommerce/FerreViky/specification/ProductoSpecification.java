package com.ecommerce.FerreViky.specification;

import com.ecommerce.FerreViky.models.Producto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductoSpecification {

    /** Solo productos activos (visibles en la tienda). */
    public static Specification<Producto> soloActivos() {
        return (root, query, cb) -> cb.isTrue(root.get("activo"));
    }

    /**
     * Cada palabra del texto debe aparecer en al menos uno de los campos.
     */
    public static Specification<Producto> buscarPorTexto(String texto) {
        return (root, query, cb) -> {
            if (texto == null || texto.isBlank()) {
                return cb.conjunction(); // sin filtro
            }

            List<Predicate> porPalabra = new ArrayList<>();
            for (String palabra : texto.trim().toLowerCase().split("\\s+")) {
                String patron = "%" + palabra + "%";
                porPalabra.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), patron),
                        cb.like(cb.lower(root.get("sku")), patron),
                        cb.like(cb.lower(root.get("plataforma")), patron),
                        cb.like(cb.lower(root.get("descripcion")), patron)
                ));
            }
            return cb.and(porPalabra.toArray(new Predicate[0]));
        };
    }

    public static Specification<Producto> conPlataforma(String plataforma) {
        return (root, query, cb) ->
                (plataforma == null || plataforma.isBlank())
                        ? cb.conjunction()
                        : cb.equal(root.get("plataforma"), plataforma);
    }

    public static Specification<Producto> conCategoria(Long categoriaId) {
        return (root, query, cb) ->
                categoriaId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("categoria").get("id"), categoriaId);
    }
}
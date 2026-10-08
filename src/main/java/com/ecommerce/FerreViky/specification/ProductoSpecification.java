package com.ecommerce.FerreViky.specification;

import com.ecommerce.FerreViky.models.Producto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductoSpecification {


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
                        cb.like(cb.lower(root.get("descripcion")), patron),
                        cb.like(cb.lower(root.get("codigo")), patron),
                        cb.like(cb.lower(root.get("clave")), patron),
                        cb.like(cb.lower(root.get("marca")), patron)
                ));
            }
            return cb.and(porPalabra.toArray(new Predicate[0]));
        };
    }

    public static Specification<Producto> conMarca(String marca) {
        return (root, query, cb) ->
                (marca == null || marca.isBlank())
                        ? cb.conjunction()
                        : cb.equal(root.get("marca"), marca);
    }

    public static Specification<Producto> conGrupo(Long grupoId) {
        return (root, query, cb) ->
                grupoId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("grupoDeProductos").get("id"), grupoId);
    }
}
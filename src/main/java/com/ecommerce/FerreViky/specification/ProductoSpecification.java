package com.ecommerce.FerreViky.specification;

import com.ecommerce.FerreViky.models.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductoSpecification {

    /** Matches active products visible in the storefront. */
    public static Specification<Product> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /**
     * Cada palabra del texto debe aparecer en al menos uno de los campos.
     */
    public static Specification<Product> byText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction(); // No filter. / Sin filtro.
            }

            List<Predicate> wordPredicates = new ArrayList<>();
            for (String word : text.trim().toLowerCase().split("\\s+")) {
                String pattern = "%" + word + "%";
                wordPredicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("platform")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            return cb.and(wordPredicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Product> withPlatform(String platform) {
        return (root, query, cb) ->
                (platform == null || platform.isBlank())
                        ? cb.conjunction()
                        : cb.equal(root.get("platform"), platform);
    }

    public static Specification<Product> withCategory(Long categoryId) {
        return (root, query, cb) ->
                categoryId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("category").get("id"), categoryId);
    }
}
package com.ecommerce.FerreViky.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Internal SKU, e.g. "STEAM-MX-200". / SKU interno, por ejemplo: "STEAM-MX-200".
    @NotBlank
    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name; // Example: "Steam Gift Card $200 MXN". / Ejemplo: "Steam Gift Card $200 MXN".

    @NotBlank
    @Column(name = "slug", nullable = false, unique = true)
    private String slug; // "steam-gift-card-200-mxn"

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "platform")
    private String platform; // Steam, PlayStation, Xbox, Netflix, Windows, etc. / Steam, PlayStation, Xbox, Netflix, Windows, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ProductType type;

    @Column(name = "region")
    private String region; // MX, US, GLOBAL...

    @Column(name = "face_value", precision = 12, scale = 2)
    private BigDecimal faceValue; // Product face value, e.g. 200.00. / Valor nominal, por ejemplo: 200.00.

    @Column(name = "currency", length = 3)
    private String currency; // ISO 4217 currency code. / Código de moneda ISO 4217.

    @DecimalMin("0.00")
    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @DecimalMin("0.00")
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice; // Cost used to calculate the margin. / Costo usado para calcular el margen.

    @Column(name = "redemption_instructions", length = 2000)
    private String redemptionInstructions; // Instructions for redeeming the code. / Instrucciones para canjear el código.

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "views", nullable = false, columnDefinition = "bigint default 0", updatable = false)
    private Long views = 0L;

    @Column(name = "average_rating", nullable = false, columnDefinition = "double precision default 0")
    private Double averageRating = 0.0;

    @Column(name = "total_reviews", nullable = false, columnDefinition = "bigint default 0")
    private Long totalReviews = 0L;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ProductType {
        GIFT_CARD, SOFTWARE, SUSCRIPCION;

        @JsonCreator
        public static ProductType fromText(String value) {
            if (value == null || value.isBlank()) return null;
            String normalizedValue = value.trim();

            if (normalizedValue.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException(
                        "The type cannot be a number (ordinal). Use a name from: "
                                + Arrays.toString(values()));
            }
            try {
                return valueOf(normalizedValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid type. Allowed values: " + Arrays.toString(values()));
            }
        }
    }
}
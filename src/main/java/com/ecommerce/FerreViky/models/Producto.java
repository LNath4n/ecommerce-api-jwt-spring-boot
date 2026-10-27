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
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SKU interno, ej: "STEAM-MX-200"
    @NotBlank
    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @NotBlank
    @Column(name = "nombre", nullable = false)
    private String nombre; // "Steam Gift Card $200 MXN"

    @NotBlank
    @Column(name = "slug", nullable = false, unique = true)
    private String slug; // "steam-gift-card-200-mxn"

    @Column(name = "descripcion", length = 2000)
    private String descripcion;

    @Column(name = "plataforma")
    private String plataforma; // Steam, PlayStation, Xbox, Netflix, Windows...

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoProducto tipo;

    @Column(name = "region")
    private String region; // MX, US, GLOBAL...

    @Column(name = "valor_nominal", precision = 12, scale = 2)
    private BigDecimal valorNominal; // lo que vale la tarjeta (ej. 200.00)

    @Column(name = "moneda", length = 3)
    private String moneda; // MXN, USD

    @DecimalMin("0.00")
    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @DecimalMin("0.00")
    @Column(name = "precio_costo", precision = 12, scale = 2)
    private BigDecimal precioCosto; // lo que te cuesta a ti (para calcular margen)

    @Column(name = "instrucciones_canje", length = 2000)
    private String instruccionesCanje; // cómo activar el código

    @Column(name = "imagen_key")
    private String imagenKey;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria; // antes GrupoDeProductos

    @PrePersist
    void alCrear() {
        this.fechaCreacion = LocalDateTime.now();
    }

    public enum TipoProducto {
        GIFT_CARD, SOFTWARE, SUSCRIPCION;

        @JsonCreator
        public static TipoProducto desdeTexto(String valor) {
            if (valor == null || valor.isBlank()) return null;
            String limpio = valor.trim();

            if (limpio.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException(
                        "El tipo no puede ser un número (ordinal). Usa el nombre: "
                                + Arrays.toString(values()));
            }
            try {
                return valueOf(limpio.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Tipo inválido. Valores permitidos: " + Arrays.toString(values()));
            }
        }
    }
}
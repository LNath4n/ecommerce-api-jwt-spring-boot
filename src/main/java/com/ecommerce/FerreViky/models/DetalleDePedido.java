package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "detalle_pedido",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_detalle_pedido",
                columnNames = {"pedido_id", "producto_id"}
        )
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DetalleDePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Min(value = 1, message = "La cantidad mínima es 1")
    @Column(nullable = false)
    private Integer cantidad;

    // Foto del precio al momento de comprar
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;
}
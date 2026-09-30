package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "carrito_producto",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_carrito_producto",
                columnNames = {"carrito_id", "producto_id"}
        )
)
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class CarritoProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "carrito_id")
    private Carrito carrito;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;
    @Min(value = 0, message = "No puedes tener 0")
    private Integer cantidad;
}

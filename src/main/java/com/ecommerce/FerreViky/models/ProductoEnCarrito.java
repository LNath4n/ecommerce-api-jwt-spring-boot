package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity
@Table(
        name = "productos_en_carrito",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_productos_en_carrito",
                columnNames = {"carrito_id", "producto_id"}
        )
)
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class ProductoEnCarrito {
    //La anotación se lee de la entidad donde está escrita hacia el tipo del campo
    //Re Nombre la clase para mayor legibilidad de codigo
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "carrito_id")
    private Carrito carrito;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer cantidad;
}
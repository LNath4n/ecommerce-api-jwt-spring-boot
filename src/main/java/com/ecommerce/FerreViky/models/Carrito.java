package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Carrito {
    //Aqui guardamos el Cliente y su lista de productos en el Carrito
    //La anotación se lee de la entidad donde está escrita hacia el tipo del campo.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    Cliente cliente;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductoEnCarrito> productos;

    private LocalDateTime fechaCreacion;
}

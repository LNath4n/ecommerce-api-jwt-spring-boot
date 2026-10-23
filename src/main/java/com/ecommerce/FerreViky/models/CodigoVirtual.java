package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "codigo_virtual")
public class CodigoVirtual {
    //Un Producto tiene muchos CodigoVirtual, y cada código pertenece a un solo producto.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // El código en sí. Debe guardarse CIFRADO
    @Column(name = "codigo", nullable = false, unique = true, length = 500)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoCodigo estado = EstadoCodigo.DISPONIBLE;

    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;

    @Column(name = "fecha_venta")
    private LocalDateTime fechaVenta;

    // Pedidossss
    // @ManyToOne @JoinColumn(name = "pedido_id") private Pedido pedido;

    @Version // evita que dos compradores se lleven el mismo código a la vez
    private Long version;

    public enum EstadoCodigo {
        DISPONIBLE,
        RESERVADO,  // en carrito / pago pendiente
        VENDIDO,
        EXPIRADO,
        INVALIDO
    }
}
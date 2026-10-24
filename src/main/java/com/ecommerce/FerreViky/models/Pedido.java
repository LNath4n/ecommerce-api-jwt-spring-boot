package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "comprador_id", nullable = false)
    private Cliente comprador;

    @Column(name = "total_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoPedido estado = EstadoPedido.PENDIENTE_PAGO;

    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    void alCrear() {
        this.fechaCreacion = LocalDateTime.now();
    }

    public enum EstadoPedido {
        PENDIENTE_PAGO,
        PAGADO,
        CANCELADO,
        FALLIDO,
        REEMBOLSADO
    }
}
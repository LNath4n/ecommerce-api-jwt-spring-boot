package com.ecommerce.FerreViky.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "resena",
        uniqueConstraints = @UniqueConstraint(columnNames = {"producto_id", "cliente_id"}))
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Min(1) @Max(5)
    @Column(nullable = false)
    private Integer calificacion;

    @Size(max = 1000)
    @Column(length = 1000)
    private String comentario;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
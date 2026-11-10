package com.ecommerce.FerreViky.models;

import com.ecommerce.FerreViky.utils.CodeCryptoConverter;
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
@Table(name = "virtual_code")
public class VirtualCode {

    // A product can have many virtual codes, and each code belongs to one product.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // The code itself. Stored encrypted (AES-GCM) via the converter.
    @Convert(converter = CodeCryptoConverter.class)
    @Column(name = "code", nullable = false, length = 500)
    private String code;

    // HMAC-SHA256 of the code: only for uniqueness and lookups.
    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CodeStatus status = CodeStatus.AVAILABLE;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @ManyToOne @JoinColumn(name = "order_id")
    private Orders orders;

    @Version // Prevents two buyers from claiming the same code at once.
    private Long version;

    public enum CodeStatus {
        AVAILABLE,
        RESERVED, // In cart / payment pending.
        SOLD,
        EXPIRED,
        INVALID
    }
}
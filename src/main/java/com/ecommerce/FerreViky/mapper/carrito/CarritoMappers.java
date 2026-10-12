package com.ecommerce.FerreViky.mapper.carrito;

import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.CarritoProductoDTO;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.CarritoProducto;
import com.ecommerce.FerreViky.models.Producto;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public class CarritoMappers {

    /**
     * Convierte una línea del carrito a DTO.
     *
     * @param cp          línea del carrito
     * @param urlResolver obtiene la URL pública de la imagen de un producto
     *                    (puede devolver {@code null} si no tiene imagen)
     * @return DTO de la línea con {@code imagenUrl}
     */
    public static CarritoProductoDTO toCarritoProductoDTO(CarritoProducto cp,
                                                          Function<Producto, String> urlResolver) {
        Producto p = cp.getProducto();
        return new CarritoProductoDTO(
                p.getId(),
                p.getDescripcion(),
                p.getClave(),
                cp.getCantidad(),
                p.getPrecioPublicoIva(),
                urlResolver.apply(p)
        );
    }

    /**
     * Convierte un carrito a su DTO de respuesta, calculando el subtotal.
     *
     * @param carrito     carrito a convertir
     * @param urlResolver obtiene la URL pública de la imagen de un producto
     * @return DTO con productos (incluyendo {@code imagenUrl}) y subtotal
     */
    public static CarritoResponseDTO toCarritoResponseDTO(Carrito carrito,
                                                          Function<Producto, String> urlResolver) {
        List<CarritoProductoDTO> productos = carrito.getProductos().stream()
                .map(cp -> toCarritoProductoDTO(cp, urlResolver))
                .toList();

        BigDecimal subtotal = productos.stream()
                .map(p -> p.precioPublicoIva().multiply(BigDecimal.valueOf(p.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CarritoResponseDTO(
                carrito.getId(),
                productos,
                carrito.getFechaCreacion(),
                subtotal
        );
    }
}
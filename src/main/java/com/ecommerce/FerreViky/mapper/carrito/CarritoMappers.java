package com.ecommerce.FerreViky.mapper.carrito;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoProductoDTO;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.ProductoEnCarrito;
import com.ecommerce.FerreViky.models.Producto;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public class CarritoMappers {

    public static CarritoProductoDTO toCarritoProductoDTO(ProductoEnCarrito cp,
                                                          Function<Producto, String> urlResolver) {
        Producto p = cp.getProducto();
        return new CarritoProductoDTO(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getSku(),
                p.getPlataforma(),
                cp.getCantidad(),
                p.getPrecioVenta(),
                urlResolver.apply(p)
        );
    }

    public static CarritoResponseDTO toCarritoResponseDTO(Carrito carrito,
                                                          Function<Producto, String> urlResolver) {
        List<CarritoProductoDTO> productos = carrito.getProductos().stream()
                .map(cp -> toCarritoProductoDTO(cp, urlResolver))
                .toList();

        BigDecimal subtotal = productos.stream()
                .map(p -> p.precioVenta().multiply(BigDecimal.valueOf(p.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CarritoResponseDTO(
                productos,
                carrito.getFechaCreacion(),
                subtotal
        );
    }
}
package com.ecommerce.FerreViky.mapper.carrito;

import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.CarritoProductoDTO;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.CarritoProducto;

import java.math.BigDecimal;
import java.util.List;

public class CarritoMappers {

    public static CarritoProductoDTO toCarritoProductoDTO(CarritoProducto cp) {
        return new CarritoProductoDTO(
                cp.getProducto().getId(),
                cp.getProducto().getDescripcion(),
                cp.getProducto().getClave(),
                cp.getCantidad(),
                cp.getProducto().getPrecioPublicoIva()
        );
    }

    public static CarritoResponseDTO toCarritoResponseDTO(Carrito carrito) {
        List<CarritoProductoDTO> productos = carrito.getProductos().stream()
                .map(CarritoMappers::toCarritoProductoDTO)
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

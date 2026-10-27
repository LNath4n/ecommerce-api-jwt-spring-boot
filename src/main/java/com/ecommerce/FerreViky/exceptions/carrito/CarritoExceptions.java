package com.ecommerce.FerreViky.exceptions.carrito;

public class CarritoExceptions {

    public static class CantidadExcedidaException extends RuntimeException {
        public CantidadExcedidaException(Integer existencia, Integer cantidad,String nombre) {
            super("No puedes agregar "+cantidad+" cantidad de "+nombre+" solo hay "+existencia+" en existencia");
        }
        public CantidadExcedidaException(long existencia, long cantidad, String nombre) {
            super("No puedes agregar " + cantidad + " unidades de " + nombre
                    + ", solo hay " + existencia + " en existencia");
        }
    }


    public static class CarritoNoEncontrado extends RuntimeException {
        public CarritoNoEncontrado(Long id) {
            super("No existe el carrito "+ id);
        }
    }

    public static class CarritoDeClienteNoEncontrado extends RuntimeException {
        public CarritoDeClienteNoEncontrado(Long id) {
            super("No se encontro un carrito para el cliente "+ id);
        }
    }

    public static class CantidadNoValida extends RuntimeException {
        public CantidadNoValida(Integer id) {
            super("No puedes tener esta cantidad "+ id);
        }
    }

    public static class ProductoNoEncontrado extends RuntimeException {
        public ProductoNoEncontrado(Long id) {
            super("No se encontro un producto con el id "+ id);
        }
    }

    public static class ProductoYaEnCarritoException extends RuntimeException {
        public ProductoYaEnCarritoException(Long idProducto) {
            super("El producto con id " + idProducto + " ya está en el carrito");
        }
    }

    public static class ProductoNoEnCarritoException extends RuntimeException {
        public ProductoNoEnCarritoException(Long idProducto) {
            super("El producto con id " + idProducto + " no está en el carrito");
        }
    }

    public static class ErrorAlCrearCarritoException extends RuntimeException {
        public ErrorAlCrearCarritoException(Long clienteId, Throwable cause) {
            super("No se pudo crear el carrito para el cliente con ID: " + clienteId, cause);
        }
    }
}

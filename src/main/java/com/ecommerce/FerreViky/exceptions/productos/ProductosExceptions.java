package com.ecommerce.FerreViky.exceptions.productos;

public class ProductosExceptions {

    public static class ProductoNoEncontradoException extends RuntimeException {
        public ProductoNoEncontradoException(Long id) {
            super("No se encontró producto con id : " + id);
        }
    }

    public static class GrupoNoEncontradoException extends RuntimeException {
        public GrupoNoEncontradoException(Long id) {
            super("No se encontró grupo de productos con id : " + id);
        }
    }

    public static class ImagenInvalidaException extends RuntimeException {
        public ImagenInvalidaException(String mensaje) {
            super(mensaje);
        }
    }
}

package com.ecommerce.FerreViky.exceptions.productos;

public class ProductosExceptions {

    public static class ProductoNoEncontradoException extends RuntimeException {
        public ProductoNoEncontradoException(Long id) {
            super("No se encontró producto con id : " + id);
        }

        public ProductoNoEncontradoException(String slug) {
            super("No se encontró producto con el slug : " + slug);
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

    public static class CategoriaNoEncontradaException extends RuntimeException{
        public CategoriaNoEncontradaException(Long id){
            super("No se encontro un producto con el ID :"+id);
        }
    }

    public static class ProductoConDependenciasException extends RuntimeException{
        public ProductoConDependenciasException(Long id){
            super("El producto con ID : " +id+" no puede ser borrado por conflictos");
        }
    }
}

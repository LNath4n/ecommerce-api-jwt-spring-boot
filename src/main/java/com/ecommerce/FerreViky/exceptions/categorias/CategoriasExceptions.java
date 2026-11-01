package com.ecommerce.FerreViky.exceptions.categorias;

public class CategoriasExceptions {

    public static class CategoriaDuplicadaException extends RuntimeException {
        public CategoriaDuplicadaException(String nombre) {
            super("Ya existe una categoría con el nombre: " + nombre);
        }
    }

    public static class CategoriaConProductosException extends RuntimeException {
        public CategoriaConProductosException(Long id) {
            super("La categoría con ID : " + id + " tiene productos asociados y no puede ser borrada");
        }
    }
}
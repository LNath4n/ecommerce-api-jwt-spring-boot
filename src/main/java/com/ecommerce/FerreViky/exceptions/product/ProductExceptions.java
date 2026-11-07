package com.ecommerce.FerreViky.exceptions.product;

public class ProductExceptions {

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(Long id) {
            super("Product not found with ID: " + id);
        }

        public ProductNotFoundException(String slug) {
            super("Product not found with slug: " + slug);
        }
    }

    public static class ProductGroupNotFoundException extends RuntimeException {
        public ProductGroupNotFoundException(Long id) {
            super("Product group not found with ID: " + id);
        }
    }

    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    public static class CategoryNotFoundException extends RuntimeException{
        public CategoryNotFoundException(Long id){
            super("Category not found with ID: " + id);
        }
    }

    public static class ProductHasDependenciesException extends RuntimeException{
        public ProductHasDependenciesException(Long id){
            super("Product with ID " + id + " cannot be deleted because it has dependencies");
        }
    }
    public static class DuplicateSkuException extends RuntimeException {
        public DuplicateSkuException(String sku) {
            super("A product with SKU " + sku + " already exists");
        }
    }
    public static class ProductModifiedException extends RuntimeException {
        public ProductModifiedException(Long id) {
            super("Product " + id + " was modified by someone else. Reload and try again.");
        }
    }
}

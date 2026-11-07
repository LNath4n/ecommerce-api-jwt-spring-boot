package com.ecommerce.FerreViky.exceptions.category;

public class CategoryExceptions {

    public static class DuplicateCategoryException extends RuntimeException {
        public DuplicateCategoryException(String name) {
            super("A category named " + name + " already exists");
        }
    }

    public static class CategoryHasProductsException extends RuntimeException {
        public CategoryHasProductsException(Long id) {
            super("Category with ID " + id + " has associated products and cannot be deleted");
        }
    }
}
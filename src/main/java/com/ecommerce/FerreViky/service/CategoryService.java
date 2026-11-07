package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.category.CategoryDTO.CategoryRequest;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.exceptions.categorias.CategoriasExceptions.CategoriaConProductosException;
import com.ecommerce.FerreViky.exceptions.categorias.CategoriasExceptions.CategoriaDuplicadaException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.CategoriaNoEncontradaException;
import com.ecommerce.FerreViky.models.Category;
import com.ecommerce.FerreViky.repository.CategoryRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * Creates a new category.
     *
     * <p>The name is trimmed and checked against existing categories, ignoring
     * capitalization. The database's {@code unique} constraint prevents duplicates
     * when requests run concurrently.</p>
     *
     * @param request category details
     * @return the created category in summary format
     * @throws CategoriaDuplicadaException if a category with that name already exists
     */
    @Transactional
    public CategoryOptionResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new CategoriaDuplicadaException(name);
        }

        Category newCategory = new Category();
        newCategory.setName(name);

        try {
            return toResponse(categoryRepository.saveAndFlush(newCategory));
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaDuplicadaException(name);
        }
    }

    /**
     * Updates the name of an existing category.
     *
     * @param id category ID
     * @param request updated category details
     * @return the updated category in summary format
     * @throws CategoriaNoEncontradaException if the category does not exist
     * @throws CategoriaDuplicadaException if another category already uses that name
     */
    @Transactional
    public CategoryOptionResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoriaNoEncontradaException(id));

        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new CategoriaDuplicadaException(name);
        }

        category.setName(name);

        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaDuplicadaException(name);
        }

        return toResponse(category);
    }

    /**
     * Deletes a category.
     *
     * <p>A category with associated products cannot be deleted; reassign those
     * products or remove their category first.</p>
     *
     * @param id category ID
     * @throws CategoriaNoEncontradaException if the category does not exist
     * @throws CategoriaConProductosException if the category has associated products
     */
    @Transactional
    public void deleteCategory(Long id) {
        if (productRepository.existsByCategoryId(id)) {
            throw new CategoriaConProductosException(id);
        }

        try {
            int deletedRows = categoryRepository.deleteCategoryById(id);
            if (deletedRows == 0) {
                throw new CategoriaNoEncontradaException(id);
            }
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaConProductosException(id);
        }
    }

    private CategoryOptionResponse toResponse(Category category) {
        return new CategoryOptionResponse(category.getId(), category.getName());
    }
}
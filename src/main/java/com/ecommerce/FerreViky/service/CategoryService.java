package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.category.CategoryDTO.CategoryRequest;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.exceptions.category.CategoryExceptions.CategoryHasProductsException;
import com.ecommerce.FerreViky.exceptions.category.CategoryExceptions.DuplicateCategoryException;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.CategoryNotFoundException;
import com.ecommerce.FerreViky.models.Category;
import com.ecommerce.FerreViky.repository.CategoryRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

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
     * @throws DuplicateCategoryException if a category with that name already exists
     */
    @Transactional
    public CategoryOptionResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        log.debug("Creating category '{}'", name);

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            log.warn("Cannot create category '{}': name already exists", name);
            throw new DuplicateCategoryException(name);
        }

        Category newCategory = new Category();
        newCategory.setName(name);

        try {
            Category saved = categoryRepository.saveAndFlush(newCategory);
            log.info("Category created: id={}, name='{}'", saved.getId(), saved.getName());
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Cannot create category '{}': duplicate detected by database constraint", name);
            throw new DuplicateCategoryException(name);
        }
    }

    /**
     * Updates the name of an existing category.
     *
     * @param id category ID
     * @param request updated category details
     * @return the updated category in summary format
     * @throws CategoryNotFoundException if the category does not exist
     * @throws DuplicateCategoryException if another category already uses that name
     */
    @Transactional
    public CategoryOptionResponse updateCategory(Long id, CategoryRequest request) {
        log.debug("Updating category id={}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cannot update category: id={} not found", id);
                    return new CategoryNotFoundException(id);
                });

        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            log.warn("Cannot update category id={}: name '{}' already used by another category", id, name);
            throw new DuplicateCategoryException(name);
        }

        category.setName(name);

        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Cannot update category id={}: duplicate name '{}' detected by database constraint", id, name);
            throw new DuplicateCategoryException(name);
        }

        log.info("Category updated: id={}, name='{}'", id, name);
        return toResponse(category);
    }

    /**
     * Deletes a category.
     *
     * <p>A category with associated products cannot be deleted; reassign those
     * products or remove their category first.</p>
     *
     * @param id category ID
     * @throws CategoryNotFoundException if the category does not exist
     * @throws CategoryHasProductsException if the category has associated products
     */
    @Transactional
    public void deleteCategory(Long id) {
        log.debug("Deleting category id={}", id);

        if (productRepository.existsByCategoryId(id)) {
            log.warn("Cannot delete category id={}: it has associated products", id);
            throw new CategoryHasProductsException(id);
        }

        try {
            int deletedRows = categoryRepository.deleteCategoryById(id);
            if (deletedRows == 0) {
                log.warn("Cannot delete category: id={} not found", id);
                throw new CategoryNotFoundException(id);
            }
            log.info("Category deleted: id={}", id);
        } catch (DataIntegrityViolationException e) {
            log.warn("Cannot delete category id={}: products associated detected by database constraint", id);
            throw new CategoryHasProductsException(id);
        }
    }

    private CategoryOptionResponse toResponse(Category category) {
        return new CategoryOptionResponse(category.getId(), category.getName());
    }
}
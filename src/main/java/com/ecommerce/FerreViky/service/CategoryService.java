package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.categoria.CategoriaDTO.CategoriaRequest;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.CategoriaOpcionResponse;
import com.ecommerce.FerreViky.exceptions.categorias.CategoriasExceptions.CategoriaConProductosException;
import com.ecommerce.FerreViky.exceptions.categorias.CategoriasExceptions.CategoriaDuplicadaException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.CategoriaNoEncontradaException;
import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.repository.CategoriaRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class CategoryService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

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
    public CategoriaOpcionResponse createCategory(CategoriaRequest request) {
        String nombre = request.nombre().trim();

        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new CategoriaDuplicadaException(nombre);
        }

        Categoria nueva = new Categoria();
        nueva.setNombre(nombre);

        try {
            return toResponse(categoriaRepository.saveAndFlush(nueva));
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaDuplicadaException(nombre); // Race condition / Carrera
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
    public CategoriaOpcionResponse updateCategory(Long id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new CategoriaNoEncontradaException(id));

        String nombre = request.nombre().trim();

        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new CategoriaDuplicadaException(nombre);
        }

        categoria.setNombre(nombre);

        try {
            categoriaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaDuplicadaException(nombre);
        }

        return toResponse(categoria);
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
        if (productoRepository.existsByCategoriaId(id)) {
            throw new CategoriaConProductosException(id);
        }

        try {
            int filas = categoriaRepository.borrarPorId(id);
            if (filas == 0) {
                throw new CategoriaNoEncontradaException(id);
            }
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaConProductosException(id);
        }
    }

    private CategoriaOpcionResponse toResponse(Categoria c) {
        return new CategoriaOpcionResponse(c.getId(), c.getNombre());
    }
}
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
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    /**
     * Crea una categoría nueva.
     *
     * <p>El nombre se recorta y se valida que no exista otra categoría con el
     * mismo nombre (sin distinguir mayúsculas). La restricción {@code unique}
     * de la base de datos cubre el caso de concurrencia.</p>
     *
     * @param request datos de la categoría a crear
     * @return la categoría creada en formato resumido
     * @throws CategoriaDuplicadaException si ya existe una categoría con ese nombre
     */
    @Transactional
    public CategoriaOpcionResponse crearCategoria(CategoriaRequest request) {
        String nombre = request.nombre().trim();

        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new CategoriaDuplicadaException(nombre);
        }

        Categoria nueva = new Categoria();
        nueva.setNombre(nombre);

        try {
            return toResponse(categoriaRepository.saveAndFlush(nueva));
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaDuplicadaException(nombre); // carrera
        }
    }

    /**
     * Edita el nombre de una categoría existente.
     *
     * @param id      identificador de la categoría
     * @param request nuevos datos de la categoría
     * @return la categoría actualizada en formato resumido
     * @throws CategoriaNoEncontradaException si no existe una categoría con ese id
     * @throws CategoriaDuplicadaException    si otra categoría ya usa ese nombre
     */
    @Transactional
    public CategoriaOpcionResponse editarCategoria(Long id, CategoriaRequest request) {
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
     * Elimina una categoría.
     *
     * <p>Si tiene productos asociados no se puede borrar; hay que reasignar o
     * quitar la categoría de esos productos primero.</p>
     *
     * @param id identificador de la categoría
     * @throws CategoriaNoEncontradaException  si no existe una categoría con ese id
     * @throws CategoriaConProductosException  si tiene productos asociados
     */
    @Transactional
    public void borrarCategoria(Long id) {
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
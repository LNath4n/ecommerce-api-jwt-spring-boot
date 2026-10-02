package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.grupoDeProductos.GrupoDeProductosMappers;
import com.ecommerce.FerreViky.mapper.producto.ProductoMappers;
import com.ecommerce.FerreViky.models.GrupoDeProductos;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.GrupoDeProductosRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final GrupoDeProductosRepository grupoDeProductosRepository;

    /**
     * Obtiene todos los productos paginados.
     *
     * @param pageable configuración de paginación y ordenamiento
     * @return página de productos en formato público
     */
    public Page<ProductoPublicoResponse> obtenerTodos(Pageable pageable) {
        return productoRepository.findAllWithGrupo(pageable)
                .map(ProductoMappers::toPublicoResponse);
    }

    /**
     * Obtiene un producto por su ID.
     *
     * @param id ID del producto a buscar
     * @return producto encontrado en formato público
     * @throws ProductoNoEncontradoException si no existe el producto
     */
    public ProductoPublicoResponse obtenerProductoPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));

        return ProductoMappers.toPublicoResponse(producto);
    }

    /**
     * Obtiene todos los grupos de productos paginados.
     *
     * @param pageable configuración de paginación y ordenamiento
     * @return página de grupos en formato público
     */
    public Page<GrupoPublicoResponse> obtenerTodosLosGrupos(Pageable pageable) {
        Page<GrupoDeProductos> pageGrupos = grupoDeProductosRepository.findAllPaged(pageable);
        List<GrupoDeProductos> conEstilos = grupoDeProductosRepository.findWithEstilos(pageGrupos.getContent());

        return pageGrupos.map(g ->
                GrupoDeProductosMappers.toPublicoResponse(
                        conEstilos.stream().filter(c -> c.getId().equals(g.getId())).findFirst().orElse(g)
                )
        );
    }

    /**
     * Obtiene un grupo de productos por su ID.
     *
     * @param id ID del grupo a buscar
     * @return grupo encontrado en formato público
     * @throws ProductoNoEncontradoException si no existe el grupo
     */
    public GrupoPublicoResponse obtenerGrupoPorId(Long id) {
        GrupoDeProductos grupoDeProductos = grupoDeProductosRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));

        return GrupoDeProductosMappers.toPublicoResponse(grupoDeProductos);
    }
}
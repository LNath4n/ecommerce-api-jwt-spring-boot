package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoOpcionResponse;
import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO;
import com.ecommerce.FerreViky.specification.ProductoSpecification;
import org.springframework.dao.DataIntegrityViolationException;
import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.GrupoNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.grupoDeProductos.GrupoDeProductosMappers;
import com.ecommerce.FerreViky.mapper.producto.ProductoMappers;
import com.ecommerce.FerreViky.models.GrupoDeProductos;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.GrupoDeProductosRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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


    /**
     * Crea un producto nuevo a partir de los datos del request.
     * <p>
     * Si el request incluye {@code grupoDeProductosId}, el grupo debe existir; si viene
     * {@code null}, el producto se crea sin grupo.
     *
     * @param productoRequest datos del producto a crear
     * @return vista pública del producto guardado, incluyendo el {@code id} generado
     * @throws GrupoNoEncontradoException   si el grupo indicado no existe
     * @throws DataIntegrityViolationException si viola una restricción de la BD (por ejemplo, {@code clave} duplicada)
     */
    @Transactional
    public ProductoPublicoResponse crearProducto(ProductoRequest productoRequest) {
        GrupoDeProductos grupo = resolverGrupo(productoRequest.grupoDeProductosId());

        Producto p = productoRepository.save(ProductoMappers.toEntity(productoRequest, grupo));

        return ProductoMappers.toPublicoResponse(p);
    }

    /**
     * Reemplaza todos los datos de un producto existente con los del request (semántica PUT).
     * <p>
     * Los campos que lleguen {@code null} en el request se guardan como {@code null}.
     * Se hace {@code flush()} para que las violaciones de integridad (por ejemplo, una
     * {@code clave} duplicada) se detecten dentro de este método.
     *
     * @param productoRequest nuevos datos del producto
     * @param idProducto      id del producto a editar
     * @return vista pública del producto ya actualizado
     * @throws ProductoNoEncontradoException   si no existe un producto con ese id
     * @throws GrupoNoEncontradoException      si el grupo indicado no existe
     * @throws DataIntegrityViolationException si viola una restricción de la BD
     */
    @Transactional
    public ProductoPublicoResponse editarProducto(ProductoRequest productoRequest, Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        GrupoDeProductos grupo = resolverGrupo(productoRequest.grupoDeProductosId());

        ProductoMappers.actualizarEntidad(p, productoRequest, grupo);
        productoRepository.flush(); //Manda ya a la base de datos todos los cambios pendientes

        return ProductoMappers.toPublicoResponse(p);
    }

    /**
     * Elimina un producto por su id.
     * <p>
     * Si el producto está referenciado por otras tablas (por ejemplo, pedidos), la BD
     * rechazará el borrado por la llave foránea. Como el {@code DELETE} se ejecuta hasta
     * el commit, esa violación puede salir fuera de este método; con {@code flush()} se
     * detectaría aquí mismo.
     *
     * @param idProducto id del producto a eliminar
     * @throws ProductoNoEncontradoException   si no existe un producto con ese id
     * @throws DataIntegrityViolationException si el producto está en uso y no puede borrarse
     */
    @Transactional
    public void borrarProducto(Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        productoRepository.delete(p);
    }

    /**
     * Obtiene un producto por su id con la vista de administración.
     * <p>
     * Solo lectura: no modifica nada en la base de datos.
     *
     * @param id id del producto a consultar
     * @return vista de administración del producto encontrado
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     */

    public ProductoAdminResponse obtenerUnProducto(Long id) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));

        return ProductoMappers.toAdminResponse(p);
    }

    /**
     * Obtiene la lista de marcas distintas que existen en el catálogo.
     * <p>
     * Excluye marcas {@code null} o vacías y las devuelve ordenadas alfabéticamente.
     *
     * @return lista de marcas sin repetir
     */
    public List<String> obtenerMarcas() {
        return productoRepository.findMarcasDistintas();
    }

    /**
     * Obtiene la lista completa de grupos de productos en formato resumido (id y nombre).
     * <p>
     * No está paginada y no incluye los productos ni los estilos del grupo.
     *
     * @return lista de grupos ordenada por nombre
     */
    public List<GrupoOpcionResponse> obtenerListaDeGrupos() {
        return grupoDeProductosRepository.findAll(Sort.by("nombre")).stream()
                .map(g -> new GrupoOpcionResponse(g.getId(), g.getNombre()))
                .toList();
    }

    /**
     * Busca productos por texto libre, con filtros opcionales de marca y grupo.
     * <p>
     * Todos los parámetros son opcionales; si ninguno viene, devuelve todos los productos paginados.
     *
     * @param texto    palabras a buscar en descripción, código, clave y marca
     * @param marca    marca exacta, o {@code null} para no filtrar
     * @param grupoId  id del grupo, o {@code null} para no filtrar
     * @param pageable configuración de paginación y ordenamiento
     * @return página de productos que cumplen los filtros, en formato público
     */
    public Page<ProductoPublicoResponse> buscar(String texto, String marca, Long grupoId, Pageable pageable) {
        Specification<Producto> spec = ProductoSpecification.buscarPorTexto(texto)
                .and(ProductoSpecification.conMarca(marca))
                .and(ProductoSpecification.conGrupo(grupoId));

        return productoRepository.findAll(spec, pageable)
                .map(ProductoMappers::toPublicoResponse);
    }

    /**
     * Busca el grupo de productos asociado a un id.
     *
     * @param grupoId id del grupo, o {@code null} si el producto no pertenece a ninguno
     * @return el grupo encontrado, o {@code null} si {@code grupoId} es {@code null}
     * @throws GrupoNoEncontradoException si el id no es {@code null} y no existe el grupo
     */
    private GrupoDeProductos resolverGrupo(Long grupoId) {
        if (grupoId == null) {
            return null; // FK nullable: "sin grupo" es válido
        }
        return grupoDeProductosRepository.findById(grupoId)
                .orElseThrow(() -> new GrupoNoEncontradoException(grupoId));
    }

}
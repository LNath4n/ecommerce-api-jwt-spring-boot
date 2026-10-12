package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoOpcionResponse;
import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.GrupoNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.grupoDeProductos.GrupoDeProductosMappers;
import com.ecommerce.FerreViky.mapper.producto.ProductoMappers;
import com.ecommerce.FerreViky.models.GrupoDeProductos;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.GrupoDeProductosRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.specification.ProductoSpecification;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@AllArgsConstructor
@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final GrupoDeProductosRepository grupoDeProductosRepository;
    private final StorageService storageService;

    /**
     * Convierte un producto a su vista pública resolviendo la URL de su imagen.
     *
     * @param p producto a convertir
     * @return vista pública con {@code imagenUrl} ({@code null} si no tiene imagen)
     */
    private ProductoPublicoResponse toPublico(Producto p) {
        return ProductoMappers.toPublicoResponse(p, storageService.getPublicUrl(p.getImagenKey()));
    }

    /**
     * Convierte un producto a su vista de administración resolviendo la URL de su imagen.
     *
     * @param p producto a convertir
     * @return vista de administración con {@code imagenUrl} ({@code null} si no tiene imagen)
     */
    private ProductoAdminResponse toAdmin(Producto p) {
        return ProductoMappers.toAdminResponse(p, storageService.getPublicUrl(p.getImagenKey()));
    }

    /**
     * Convierte un grupo a su vista pública, incluyendo las URLs de imagen de sus
     * productos (máximo 5, sin nulos) para el carrusel.
     *
     * @param g grupo a convertir
     * @return vista pública del grupo con {@code productoIds} e {@code imagenUrls}
     */
    private GrupoPublicoResponse toGrupoPublico(GrupoDeProductos g) {
        return GrupoDeProductosMappers.toPublicoResponse(
                g, p -> storageService.getPublicUrl(p.getImagenKey()));
    }

    /**
     * Obtiene todos los productos paginados.
     *
     * @param pageable configuración de paginación y ordenamiento
     * @return página de productos en formato público
     */
    public Page<ProductoPublicoResponse> obtenerTodos(Pageable pageable) {
        return productoRepository.findAllWithGrupo(pageable)
                .map(this::toPublico);
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

        return toPublico(producto);
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
                toGrupoPublico(
                        conEstilos.stream().filter(c -> c.getId().equals(g.getId())).findFirst().orElse(g)
                )
        );
    }

    /**
     * Obtiene un grupo de productos por su ID.
     *
     * @param id ID del grupo a buscar
     * @return grupo encontrado en formato público
     * @throws GrupoNoEncontradoException si no existe el grupo
     */
    public GrupoPublicoResponse obtenerGrupoPorId(Long id) {
        GrupoDeProductos grupoDeProductos = grupoDeProductosRepository.findById(id)
                .orElseThrow(() -> new GrupoNoEncontradoException(id));

        return toGrupoPublico(grupoDeProductos);
    }

    /**
     * Crea un producto nuevo a partir de los datos del request.
     * <p>
     * Si el request incluye {@code grupoDeProductosId}, el grupo debe existir; si viene
     * {@code null}, el producto se crea sin grupo. La imagen se sube aparte con
     * {@link #subirImagen(Long, MultipartFile)}.
     *
     * @param productoRequest datos del producto a crear
     * @return vista pública del producto guardado, incluyendo el {@code id} generado
     * @throws GrupoNoEncontradoException      si el grupo indicado no existe
     * @throws DataIntegrityViolationException si viola una restricción de la BD (por ejemplo, {@code clave} duplicada)
     */
    @Transactional
    public ProductoPublicoResponse crearProducto(ProductoRequest productoRequest) {
        GrupoDeProductos grupo = resolverGrupo(productoRequest.grupoDeProductosId());

        Producto p = productoRepository.save(ProductoMappers.toEntity(productoRequest, grupo));

        return toPublico(p);
    }

    /**
     * Reemplaza todos los datos de un producto existente con los del request (semántica PUT).
     * <p>
     * Los campos que lleguen {@code null} se guardan como {@code null}, excepto la imagen,
     * que no se toca (se administra con {@link #subirImagen(Long, MultipartFile)}).
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
        productoRepository.flush(); // Manda ya a la BD los cambios pendientes

        return toPublico(p);
    }

    /**
     * Elimina un producto por su id y, si tenía, su imagen del bucket.
     * <p>
     * Se hace {@code flush()} antes de borrar la imagen: si la BD rechaza el borrado
     * (por ejemplo, el producto está en pedidos), la excepción sale aquí y la imagen
     * se conserva.
     *
     * @param idProducto id del producto a eliminar
     * @throws ProductoNoEncontradoException   si no existe un producto con ese id
     * @throws DataIntegrityViolationException si el producto está en uso y no puede borrarse
     */
    @Transactional
    public void borrarProducto(Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        String imagenKey = p.getImagenKey();

        productoRepository.delete(p);
        productoRepository.flush();

        storageService.delete(imagenKey);
    }

    /**
     * Obtiene un producto por su id con la vista de administración.
     *
     * @param id id del producto a consultar
     * @return vista de administración del producto encontrado
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     */
    public ProductoAdminResponse obtenerUnProducto(Long id) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));

        return toAdmin(p);
    }

    /**
     * Sube (o reemplaza) la imagen de un producto.
     * <p>
     * Sube la nueva al bucket, guarda su key en la BD y, solo después, borra la anterior.
     *
     * @param idProducto id del producto
     * @param file       imagen a subir
     * @return vista de administración con la nueva {@code imagenUrl}
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     * @throws IOException                   si falla la lectura del archivo
     */
    @Transactional
    public ProductoAdminResponse subirImagen(Long idProducto, MultipartFile file) throws IOException {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        String keyAnterior = p.getImagenKey();
        p.setImagenKey(storageService.upload(file));
        productoRepository.flush();

        storageService.delete(keyAnterior); // borra la vieja para no dejar basura en el bucket
        return toAdmin(p);
    }

    /**
     * Obtiene la lista de marcas distintas del catálogo, sin nulas ni vacías, ordenadas.
     *
     * @return lista de marcas sin repetir
     */
    public List<String> obtenerMarcas() {
        return productoRepository.findMarcasDistintas();
    }

    /**
     * Obtiene todos los grupos en formato resumido (id y nombre), sin paginar.
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
                .map(this::toPublico);
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
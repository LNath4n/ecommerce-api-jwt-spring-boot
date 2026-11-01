package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.cache.cacheEvict.EvictTopProductos;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.CategoriaOpcionResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.CategoriaNoEncontradaException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoConDependenciasException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoModificadoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.SkuDuplicadoException;
import com.ecommerce.FerreViky.mapper.producto.ProductoMappers;
import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoProductoRepository;
import com.ecommerce.FerreViky.repository.CategoriaRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import com.ecommerce.FerreViky.specification.ProductoSpecification;
import com.ecommerce.FerreViky.utils.SlugUtil;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio del catálogo de productos: consultas públicas, administración de
 * productos y manejo de sus imágenes.
 * <p>
 * El stock de un producto es la cantidad de códigos virtuales en estado
 * {@link EstadoCodigo#DISPONIBLE}.
 */
@Service
@AllArgsConstructor
public class ProductoService {

    private static final int MIN_RESENAS_TOP_CALIFICADOS = 3;

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final CarritoProductoRepository carritoProductoRepository;
    private final StorageService storageService;

    /**
     * Obtiene los productos activos de forma paginada.
     *
     * @param pageable configuración de paginación y ordenamiento
     * @return página de productos activos con sus datos públicos
     */
    public Page<ProductoPublicoResponse> obtenerTodos(Pageable pageable) {
        return toPaginaPublica(productoRepository.findByActivoTrue(pageable));
    }

    /**
     * Obtiene un producto activo por su id y registra una vista.
     *
     * @param id identificador del producto
     * @return datos públicos del producto encontrado
     * @throws ProductoNoEncontradoException si no existe o está inactivo
     */
    public ProductoPublicoResponse obtenerProductoPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
        return registrarVistaYConvertir(producto);
    }

    /**
     * Obtiene un producto activo por su slug y registra una vista.
     *
     * @param slug identificador amigable del producto
     * @return datos públicos del producto encontrado
     * @throws ProductoNoEncontradoException si no existe o está inactivo
     */
    public ProductoPublicoResponse obtenerProductoPorSlug(String slug) {
        Producto producto = productoRepository.findBySlug(slug)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(slug));
        return registrarVistaYConvertir(producto);
    }

    /**
     * Busca productos activos por texto libre, con filtros opcionales de
     * plataforma y categoría.
     *
     * <p>Los criterios se combinan mediante {@link Specification}. Cada filtro
     * recibido como {@code null} se omite de la búsqueda.</p>
     *
     * @param texto       texto libre a buscar
     * @param plataforma  plataforma por la cual filtrar
     * @param categoriaId id de la categoría por la cual filtrar
     * @param pageable    configuración de paginación y ordenamiento
     * @return página de productos activos que cumplen los criterios
     */
    public Page<ProductoPublicoResponse> buscar(String texto, String plataforma, Long categoriaId, Pageable pageable) {
        Specification<Producto> spec = ProductoSpecification.soloActivos()
                .and(ProductoSpecification.buscarPorTexto(texto))
                .and(ProductoSpecification.conPlataforma(plataforma))
                .and(ProductoSpecification.conCategoria(categoriaId));

        return toPaginaPublica(productoRepository.findAll(spec, pageable));
    }

    /**
     * Obtiene las plataformas distintas registradas en el catálogo.
     *
     * @return lista de plataformas sin repetir
     */
    public List<String> obtenerPlataformas() {
        return productoRepository.findPlataformasDistintas();
    }

    /**
     * Obtiene todas las categorías en formato resumido.
     *
     * @return lista de categorías ordenadas por nombre
     */
    public List<CategoriaOpcionResponse> obtenerListaDeCategorias() {
        return categoriaRepository.findAll(Sort.by("nombre")).stream()
                .map(c -> new CategoriaOpcionResponse(c.getId(), c.getNombre()))
                .toList();
    }

    /**
     * Obtiene los diez productos activos con más vistas.
     * <p>
     * El resultado se guarda en caché.
     *
     * @return productos públicos ordenados por número de vistas
     */
    @Cacheable(value = "topVistas", unless = "#result == null or #result.isEmpty()")
    public List<ProductoPublicoResponse> topMasVistos() {
        return aPublicos(productoRepository.findTop10ByActivoTrueOrderByVistasDesc());
    }

    /**
     * Obtiene los diez productos activos mejor calificados que tengan al menos
     * {@value #MIN_RESENAS_TOP_CALIFICADOS} reseñas.
     * <p>
     * El resultado se guarda en caché.
     *
     * @return productos públicos ordenados por calificación y número de reseñas
     */
    @Cacheable(value = "topCalificados", unless = "#result == null or #result.isEmpty()")
    public List<ProductoPublicoResponse> topMejorCalificados() {
        return aPublicos(productoRepository
                .findTop10ByActivoTrueAndTotalResenasGreaterThanEqualOrderByPromedioCalificacionDescTotalResenasDesc(
                        MIN_RESENAS_TOP_CALIFICADOS));
    }

    /**
     * Obtiene los diez productos activos con más reseñas.
     * <p>
     * El resultado se guarda en caché.
     *
     * @return productos públicos ordenados por número de reseñas
     */
    @Cacheable(value = "topResenados", unless = "#result == null or #result.isEmpty()")
    public List<ProductoPublicoResponse> topMasResenados() {
        return aPublicos(productoRepository.findTop10ByActivoTrueOrderByTotalResenasDesc());
    }

    /**
     * Obtiene la vista de administración de un producto.
     *
     * <p>A diferencia de las consultas públicas, también devuelve productos
     * inactivos.</p>
     *
     * @param id identificador del producto
     * @return datos de administración del producto
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     */
    public ProductoAdminResponse obtenerUnProducto(Long id) {
        return toAdmin(obtenerPorId(id));
    }

    /**
     * Crea un nuevo producto.
     *
     * <p>Normaliza el SKU (sin espacios y en mayúsculas), verifica que no exista
     * otro igual, resuelve la categoría (si se indica) y genera el slug a partir
     * del nombre y el SKU con {@link SlugUtil}.</p>
     *
     * <p>La unicidad final del SKU la garantiza la restricción {@code unique} de
     * la base de datos, así que un duplicado por concurrencia también se reporta
     * como {@link SkuDuplicadoException}.</p>
     *
     * @param request datos del producto a crear
     * @return datos públicos del producto creado
     * @throws SkuDuplicadoException          si ya existe un producto con ese SKU
     * @throws CategoriaNoEncontradaException si la categoría indicada no existe
     */
    @Transactional
    public ProductoPublicoResponse crearProducto(ProductoRequest request) {
        String sku = normalizarSku(request.sku());

        if (productoRepository.existsBySku(sku)) {
            throw new SkuDuplicadoException(sku);
        }

        Categoria categoria = resolverCategoria(request.categoriaId());
        Producto nuevo = ProductoMappers.toEntity(request, categoria);
        nuevo.setSku(sku);
        nuevo.setSlug(SlugUtil.slugify(request.nombre() + " " + sku));

        try {
            return toPublico(productoRepository.saveAndFlush(nuevo));
        } catch (DataIntegrityViolationException e) {
            throw new SkuDuplicadoException(sku);
        }
    }

    /**
     * Actualiza los datos de un producto existente.
     *
     * <p>Normaliza el SKU, verifica que no pertenezca a otro producto, resuelve la
     * categoría (si se indica) y aplica los cambios. Se hace {@code flush} para
     * detectar dentro de la operación las violaciones de integridad y los
     * conflictos de versión.</p>
     *
     * @param request    nuevos datos del producto
     * @param idProducto identificador del producto a editar
     * @return datos públicos del producto actualizado
     * @throws ProductoNoEncontradoException  si no existe un producto con ese id
     * @throws SkuDuplicadoException          si el SKU ya lo usa otro producto
     * @throws CategoriaNoEncontradaException si la categoría indicada no existe
     * @throws ProductoModificadoException    si otro usuario modificó el producto al mismo tiempo
     */
    @EvictTopProductos
    @Transactional
    public ProductoPublicoResponse editarProducto(ProductoRequest request, Long idProducto) {
        Producto producto = obtenerPorId(idProducto);
        String sku = normalizarSku(request.sku());

        if (productoRepository.existsBySkuAndIdNot(sku, idProducto)) {
            throw new SkuDuplicadoException(sku);
        }

        Categoria categoria = resolverCategoria(request.categoriaId());

        ProductoMappers.actualizarEntidad(producto, request, categoria);
        producto.setSku(sku);

        try {
            productoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new SkuDuplicadoException(sku);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ProductoModificadoException(idProducto);
        }

        return toPublico(producto);
    }

    /**
     * Elimina un producto y su imagen.
     *
     * <p>Si el producto tiene códigos virtuales asociados, o la base de datos
     * rechaza el borrado por otras referencias, no se elimina y se conserva la
     * imagen. Antes de borrar se quitan las líneas de carrito que lo contengan,
     * para que no bloqueen la eliminación.</p>
     *
     * <p>La imagen se borra del bucket solo si la transacción se confirma.</p>
     *
     * @param idProducto identificador del producto a eliminar
     * @throws ProductoNoEncontradoException   si no existe un producto con ese id
     * @throws ProductoConDependenciasException si tiene códigos, pedidos u otras referencias
     */
    @EvictTopProductos
    @Transactional
    public void borrarProducto(Long idProducto) {
        Producto producto = obtenerPorId(idProducto);

        if (codigoVirtualRepository.existsByProductoId(idProducto)) {
            throw new ProductoConDependenciasException(idProducto);
        }

        String imagenKey = producto.getImagenKey();

        try {
            carritoProductoRepository.eliminarPorProductoId(idProducto);
            productoRepository.delete(producto);
            productoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProductoConDependenciasException(idProducto);
        }

        storageService.deleteAfterCommit(imagenKey);
    }

    /**
     * Desactiva un producto sin eliminar su registro.
     *
     * <p>Deja de aparecer en las consultas públicas, pero conserva sus códigos,
     * pedidos e imagen. Es la alternativa a {@link #borrarProducto(Long)} cuando
     * el producto ya tiene dependencias. El cambio se persiste al confirmarse la
     * transacción.</p>
     *
     * @param idProducto identificador del producto a desactivar
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     */
    @EvictTopProductos
    @Transactional
    public void desactivarProducto(Long idProducto) {
        obtenerPorId(idProducto).setActivo(false);
    }

    /**
     * Sube o reemplaza la imagen de un producto.
     *
     * <p>Sube la nueva imagen a la carpeta de productos, guarda su key y, solo si
     * la transacción se confirma, borra la imagen anterior del bucket.</p>
     *
     * @param idProducto identificador del producto
     * @param file       imagen a subir
     * @return datos de administración del producto con su imagen actualizada
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     * @throws IOException                   si falla la lectura del archivo
     */
    @Transactional
    public ProductoAdminResponse subirImagen(Long idProducto, MultipartFile file) throws IOException {
        Producto producto = obtenerPorId(idProducto);

        String keyAnterior = producto.getImagenKey();
        producto.setImagenKey(storageService.upload(file, StorageService.Carpeta.PRODUCTOS));

        storageService.deleteAfterCommit(keyAnterior);
        return toAdmin(producto);
    }

    /**
     * Elimina la imagen de un producto.
     *
     * <p>Deja la key en {@code null} y, solo si la transacción se confirma, borra
     * el archivo del bucket. Si el producto no tenía imagen, no hay efecto.</p>
     *
     * @param idProducto identificador del producto
     * @return datos de administración del producto sin imagen
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     */
    @Transactional
    public ProductoAdminResponse quitarImagen(Long idProducto) {
        Producto producto = obtenerPorId(idProducto);

        String key = producto.getImagenKey();
        producto.setImagenKey(null);
        storageService.deleteAfterCommit(key);

        return toAdmin(producto);
    }

    /**
     * Busca un producto por id, esté activo o no.
     *
     * @param id identificador del producto
     * @return el producto encontrado
     * @throws ProductoNoEncontradoException si no existe un producto con ese id
     */
    private Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    /**
     * Resuelve una categoría a partir de su id.
     *
     * @param categoriaId id de la categoría, o {@code null} si el producto no tiene
     * @return la categoría encontrada, o {@code null} si el id es {@code null}
     * @throws CategoriaNoEncontradaException si el id no es {@code null} y no existe
     */
    private Categoria resolverCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    }

    /**
     * Quita espacios y pasa el SKU a mayúsculas para compararlo de forma consistente.
     *
     * @param sku SKU recibido
     * @return SKU normalizado
     */
    private String normalizarSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Registra una vista del producto y lo convierte a su representación pública.
     *
     * <p>La vista se incrementa en la base de datos y también en la entidad, para
     * que la respuesta muestre el valor actualizado.</p>
     *
     * @param producto producto cuya vista se registra
     * @return datos públicos del producto con el contador de vistas actualizado
     */
    private ProductoPublicoResponse registrarVistaYConvertir(Producto producto) {
        productoRepository.incrementarVistas(producto.getId());
        producto.setVistas(producto.getVistas() + 1);
        return toPublico(producto);
    }

    /**
     * Calcula el stock de un solo producto.
     *
     * @param producto producto cuyo stock se consulta
     * @return cantidad de códigos disponibles
     */
    private int stockDe(Producto producto) {
        return (int) codigoVirtualRepository
                .countByProductoIdAndEstado(producto.getId(), EstadoCodigo.DISPONIBLE);
    }

    /**
     * Calcula el stock de varios productos en una sola consulta.
     *
     * @param productos productos cuyo stock se consulta
     * @return mapa de id de producto a stock disponible; los productos sin códigos
     *         disponibles no aparecen, y es vacío si no se reciben productos
     */
    private Map<Long, Integer> stockPorProducto(Collection<Producto> productos) {
        if (productos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = productos.stream().map(Producto::getId).toList();
        Map<Long, Integer> stock = new HashMap<>();
        codigoVirtualRepository.contarPorProducto(ids, EstadoCodigo.DISPONIBLE)
                .forEach(s -> stock.put(s.getProductoId(), s.getTotal().intValue()));
        return stock;
    }

    /**
     * Convierte un producto a su representación pública consultando su stock.
     *
     * @param producto entidad a convertir
     * @return datos públicos con la URL de la imagen y el stock actual
     */
    private ProductoPublicoResponse toPublico(Producto producto) {
        return toPublico(producto, stockDe(producto));
    }

    /**
     * Convierte un producto a su representación pública con un stock ya calculado.
     *
     * @param producto entidad a convertir
     * @param stock    stock disponible del producto
     * @return datos públicos con la URL de la imagen y el stock indicado
     */
    private ProductoPublicoResponse toPublico(Producto producto, int stock) {
        return ProductoMappers.toPublicoResponse(
                producto, storageService.getPublicUrl(producto.getImagenKey()), stock);
    }

    /**
     * Convierte un producto a su representación de administración.
     *
     * @param producto entidad a convertir
     * @return datos de administración con la URL de la imagen y el stock actual
     */
    private ProductoAdminResponse toAdmin(Producto producto) {
        return ProductoMappers.toAdminResponse(
                producto, storageService.getPublicUrl(producto.getImagenKey()), stockDe(producto));
    }

    /**
     * Convierte una página de productos a respuestas públicas.
     *
     * <p>El stock de toda la página se obtiene con una sola consulta. Los productos
     * sin códigos disponibles se reportan con stock {@code 0}.</p>
     *
     * @param pagina página de entidades a convertir
     * @return página con los datos públicos de cada producto
     */
    private Page<ProductoPublicoResponse> toPaginaPublica(Page<Producto> pagina) {
        Map<Long, Integer> stock = stockPorProducto(pagina.getContent());
        return pagina.map(p -> toPublico(p, stock.getOrDefault(p.getId(), 0)));
    }

    /**
     * Convierte una lista de productos a respuestas públicas.
     *
     * <p>El stock de todos los productos se obtiene con una sola consulta. Los
     * productos sin códigos disponibles se reportan con stock {@code 0}.</p>
     *
     * <p>La lista se arma como {@link ArrayList} y no con {@code .toList()}, porque
     * estos resultados se guardan en caché y la lista inmutable de {@code .toList()}
     * puede ser incompatible con el serializador configurado.</p>
     *
     * @param productos entidades a convertir
     * @return lista mutable de respuestas públicas; vacía si no hay productos
     */
    private List<ProductoPublicoResponse> aPublicos(List<Producto> productos) {
        Map<Long, Integer> stock = stockPorProducto(productos);

        return productos.stream()
                .map(p -> toPublico(p, stock.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.producto.ProductoDTO.*;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.CategoriaNoEncontradaException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.producto.ProductoMappers;
import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoProductoRepository;
import com.ecommerce.FerreViky.repository.CategoriaRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.specification.ProductoSpecification;
import com.ecommerce.FerreViky.utils.SlugUtil;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final CarritoProductoRepository carritoProductoRepository;
    private final StorageService storageService;

    /**
     * Calcula el stock de un solo producto.
     *
     * <p>El stock corresponde a la cantidad de códigos virtuales del producto
     * cuyo estado es {@link EstadoCodigo#DISPONIBLE}.</p>
     *
     * @param p producto cuyo stock se desea consultar
     * @return cantidad de códigos disponibles del producto
     */
    private int stockDe(Producto p) {
        return (int) codigoVirtualRepository
                .countByProductoIdAndEstado(p.getId(), EstadoCodigo.DISPONIBLE);
    }

    /**
     * Calcula el stock de varios productos en una sola consulta.
     *
     * @param productos productos cuyo stock se desea consultar
     * @return mapa con el id del producto como clave y su stock disponible
     *         como valor; vacío si no se proporcionan productos
     */
    private Map<Long, Integer> stockPorProducto(Collection<Producto> productos) {
        if (productos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = productos.stream().map(Producto::getId).toList();
        Map<Long, Integer> mapa = new HashMap<>();
        codigoVirtualRepository.contarPorProducto(ids, EstadoCodigo.DISPONIBLE)
                .forEach(s -> mapa.put(s.getProductoId(), s.getTotal().intValue()));
        return mapa;
    }

    /**
     * Convierte un producto en su representación pública.
     *
     * <p>Incluye la URL pública de la imagen y el stock disponible actual.</p>
     *
     * @param p entidad del producto a convertir
     * @return datos públicos del producto
     */
    private ProductoPublicoResponse toPublico(Producto p) {
        return ProductoMappers.toPublicoResponse(p, storageService.getPublicUrl(p.getImagenKey()), stockDe(p));
    }

    /**
     * Convierte un producto en su representación para administración.
     *
     * <p>Incluye la URL pública de la imagen y el stock disponible actual.</p>
     *
     * @param p entidad del producto a convertir
     * @return datos de administración del producto
     */
    private ProductoAdminResponse toAdmin(Producto p) {
        return ProductoMappers.toAdminResponse(p, storageService.getPublicUrl(p.getImagenKey()), stockDe(p));
    }

    /**
     * Convierte una página de productos en una página de respuestas públicas.
     *
     * <p>El stock de todos los productos de la página se obtiene con una sola
     * consulta mediante {@link #stockPorProducto(Collection)}. Los productos
     * sin códigos disponibles se reportan con stock {@code 0}.</p>
     *
     * @param pagina página de entidades a convertir
     * @return página con los datos públicos de cada producto
     */
    private Page<ProductoPublicoResponse> toPaginaPublica(Page<Producto> pagina) {
        Map<Long, Integer> stock = stockPorProducto(pagina.getContent());
        return pagina.map(p -> ProductoMappers.toPublicoResponse(
                p,
                storageService.getPublicUrl(p.getImagenKey()),
                stock.getOrDefault(p.getId(), 0)));
    }

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
     * Obtiene un producto activo por su id.
     *
     * @param id identificador del producto
     * @return datos públicos del producto encontrado
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id o si está inactivo
     */
    public ProductoPublicoResponse obtenerProductoPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
        return toPublico(producto);
    }

    /**
     * Busca productos activos por texto libre, con filtros opcionales de
     * plataforma y categoría.
     *
     * <p>Los criterios se combinan mediante {@link Specification}. Cada
     * filtro que se reciba como {@code null} se omite de la búsqueda.</p>
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
     * Obtiene la vista de administración de un producto.
     *
     * <p>A diferencia de las consultas públicas, esta también devuelve
     * productos inactivos.</p>
     *
     * @param id identificador del producto
     * @return datos de administración del producto
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id
     */
    public ProductoAdminResponse obtenerUnProducto(Long id) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
        return toAdmin(p);
    }

    /**
     * Crea un nuevo producto.
     *
     * <p>Resuelve la categoría indicada (si se proporciona) y genera un slug
     * único a partir del nombre mediante {@link #generarSlugUnico(String)}.</p>
     *
     * @param request datos del producto a crear
     * @return datos públicos del producto creado
     * @throws CategoriaNoEncontradaException si se indica una categoría que
     *         no existe
     */
    @Transactional
    public ProductoPublicoResponse crearProducto(ProductoRequest request) {
        Categoria categoria = resolverCategoria(request.categoriaId());
        Producto nuevo = ProductoMappers.toEntity(request, categoria);
        nuevo.setSlug(generarSlugUnico(request.nombre()));
        Producto p = productoRepository.save(nuevo);
        return toPublico(p);
    }

    /**
     * Actualiza los datos de un producto existente.
     *
     * <p>Resuelve la categoría indicada (si se proporciona) y aplica los
     * cambios sobre la entidad. Se hace {@code flush} para que las
     * violaciones de integridad se detecten dentro de la operación.</p>
     *
     * @param request    nuevos datos del producto
     * @param idProducto identificador del producto a editar
     * @return datos públicos del producto actualizado
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id
     * @throws CategoriaNoEncontradaException si se indica una categoría que
     *         no existe
     */
    @Transactional
    public ProductoPublicoResponse editarProducto(ProductoRequest request, Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        Categoria categoria = resolverCategoria(request.categoriaId());

        ProductoMappers.actualizarEntidad(p, request, categoria);
        productoRepository.flush();

        return toPublico(p);
    }

    /**
     * Obtiene un producto activo por su slug.
     *
     * @param slug identificador amigable del producto
     * @return datos públicos del producto encontrado
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese slug o si está inactivo
     */
    public ProductoPublicoResponse obtenerProductoPorSlug(String slug) {
        Producto producto = productoRepository.findBySlug(slug)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(slug));
        return toPublico(producto);
    }

    /**
     * Elimina un producto y su imagen.
     *
     * <p>Si el producto ya tiene códigos o pedidos asociados, la base de
     * datos rechaza la eliminación ({@code DataIntegrityViolationException})
     * y la imagen se conserva.</p>
     *
     * <p>La imagen se borra del bucket solo si la transacción se
     * confirma.</p>
     *
     * @param idProducto identificador del producto a eliminar
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id
     */
    @Transactional
    public void borrarProducto(Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        if (codigoVirtualRepository.existsByProductoId(idProducto)) {
            throw new ProductoConDependenciasException(idProducto);
        }

        String imagenKey = p.getImagenKey();

        try {
            carritoProductoRepository.eliminarPorProductoId(idProducto);  // limpia carrito, solo para no quedarnos esperando
            productoRepository.delete(p);
            productoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProductoConDependenciasException(idProducto);
        }

        storageService.deleteAfterCommit(imagenKey);
    }

    /**
     * Desactiva un producto sin eliminar su registro.
     *
     * <p>Cambia su estado a {@code activo = false}, por lo que deja de
     * aparecer en las consultas públicas pero conserva sus códigos, pedidos
     * e imagen. Es la alternativa a {@link #borrarProducto(Long)} cuando el
     * producto ya tiene códigos o pedidos asociados.</p>
     *
     * <p>Al ejecutarse dentro de una transacción, el cambio se persiste al
     * finalizar correctamente la operación.</p>
     *
     * @param idProducto identificador del producto a desactivar
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id
     */
    @Transactional
    public void desactivarProducto(Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        p.setActivo(false);
    }

    /**
     * Sube (o reemplaza) la imagen de un producto.
     *
     * <p>Sube la nueva imagen a la carpeta de productos, guarda su key en la
     * BD y, solo si la transacción se confirma, borra la imagen anterior del
     * bucket.</p>
     *
     * @param idProducto identificador del producto
     * @param file       imagen a subir
     * @return datos de administración del producto con su imagen actualizada
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id
     * @throws IOException si falla la lectura del archivo
     */
    @Transactional
    public ProductoAdminResponse subirImagen(Long idProducto, MultipartFile file) throws IOException {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        String keyAnterior = p.getImagenKey();
        p.setImagenKey(storageService.upload(file, StorageService.Carpeta.PRODUCTOS));

        storageService.deleteAfterCommit(keyAnterior);
        return toAdmin(p);
    }

    /**
     * Elimina la imagen de un producto.
     *
     * <p>Pone la key de la imagen en {@code null} y, solo si la transacción
     * se confirma, borra el archivo del bucket. Si el producto no tenía
     * imagen, la operación no tiene efecto.</p>
     *
     * @param idProducto identificador del producto
     * @return datos de administración del producto sin imagen
     * @throws ProductoNoEncontradoException si no existe un producto con
     *         ese id
     */
    @Transactional
    public ProductoAdminResponse quitarImagen(Long idProducto) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        String key = p.getImagenKey();
        p.setImagenKey(null);
        storageService.deleteAfterCommit(key);

        return toAdmin(p);
    }

    /**
     * Genera un slug único a partir del nombre de un producto.
     *
     * <p>Si el slug base ya existe, se le agrega un sufijo numérico
     * incremental a partir de {@code 2}. Por ejemplo:
     * {@code steam-gift-card-200-mxn-2}.</p>
     *
     * @param nombre nombre del producto
     * @return slug que no está en uso por ningún otro producto
     */
    private String generarSlugUnico(String nombre) {
        String base = SlugUtil.slugify(nombre);
        String slug = base;
        int i = 2;
        while (productoRepository.existsBySlug(slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    /**
     * Resuelve una categoría a partir de su id.
     *
     * @param categoriaId id de la categoría, o {@code null} si el producto
     *                    no tiene categoría
     * @return la categoría encontrada, o {@code null} si el id es
     *         {@code null}
     * @throws CategoriaNoEncontradaException si el id no es {@code null} y
     *         no existe una categoría con ese id
     */
    private Categoria resolverCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    }
}
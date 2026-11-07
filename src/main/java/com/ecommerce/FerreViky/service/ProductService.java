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
import org.springframework.transaction.annotation.Transactional;
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
 * Service for the product catalog, including public queries, product
 * administration, and image management.
 * <p>
 * A product's stock is the number of virtual codes in the
 * {@link EstadoCodigo#DISPONIBLE} state.
 */
@Service
@AllArgsConstructor
public class ProductService {

    private static final int MIN_RESENAS_TOP_CALIFICADOS = 3;

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final CarritoProductoRepository carritoProductoRepository;
    private final StorageService storageService;

    /**
     * Retrieves active products in paginated form.
     *
     * @param pageable pagination and sorting settings
     * @return a page of active products with their public details
     */
    public Page<ProductoPublicoResponse> getAllProducts(Pageable pageable) {
        return toPublicPage(productoRepository.findByActivoTrue(pageable));
    }

    /**
     * Retrieves an active product by ID and records a view.
     *
     * @param id product ID
     * @return the product's public details
     * @throws ProductoNoEncontradoException if the product does not exist or is inactive
     */
    public ProductoPublicoResponse getProductById(Long id) {
        Producto producto = productoRepository.findById(id)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
        return recordViewAndConvert(producto);
    }

    /**
     * Retrieves an active product by its slug and records a view.
     *
     * @param slug the product's human-readable identifier
     * @return the product's public details
     * @throws ProductoNoEncontradoException if the product does not exist or is inactive
     */
    public ProductoPublicoResponse getProductBySlug(String slug) {
        Producto producto = productoRepository.findBySlug(slug)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(slug));
        return recordViewAndConvert(producto);
    }

    /**
     * Searches active products by free text, with optional platform and
     * category filters.
     *
     * <p>Criteria are combined using {@link Specification}. Filters with a
     * {@code null} value are omitted from the search.</p>
     *
     * @param texto free text to search for
     * @param plataforma platform to filter by
     * @param categoriaId category ID to filter by
     * @param pageable pagination and sorting settings
     * @return a page of active products matching the criteria
     */
    public Page<ProductoPublicoResponse> searchProducts(String texto, String plataforma, Long categoriaId, Pageable pageable) {
        Specification<Producto> spec = ProductoSpecification.soloActivos()
                .and(ProductoSpecification.buscarPorTexto(texto))
                .and(ProductoSpecification.conPlataforma(plataforma))
                .and(ProductoSpecification.conCategoria(categoriaId));

        return toPublicPage(productoRepository.findAll(spec, pageable));
    }

    /**
     * Retrieves the distinct platforms listed in the catalog.
     *
     * @return a list of unique platforms
     */
    public List<String> getPlatforms() {
        return productoRepository.findPlataformasDistintas();
    }

    /**
     * Retrieves all categories in summary format.
     *
     * @return categories sorted by name
     */
    public List<CategoriaOpcionResponse> getCategories() {
        return categoriaRepository.findAll(Sort.by("nombre")).stream()
                .map(c -> new CategoriaOpcionResponse(c.getId(), c.getNombre()))
                .toList();
    }

    /**
     * Retrieves the ten most-viewed active products.
     * <p>
     * The result is cached.
     *
     * @return public product details ordered by view count
     */
    @Cacheable(value = "topVistas", unless = "#result == null or #result.isEmpty()")
    public List<ProductoPublicoResponse> getTopViewedProducts() {
        return toPublicResponses(productoRepository.findTop10ByActivoTrueOrderByVistasDesc());
    }

    /**
     * Retrieves the ten highest-rated active products with at least
     * {@value #MIN_RESENAS_TOP_CALIFICADOS} reviews.
     * <p>
     * The result is cached.
     *
     * @return public product details ordered by rating and review count
     */
    @Cacheable(value = "topCalificados", unless = "#result == null or #result.isEmpty()")
    public List<ProductoPublicoResponse> getTopRatedProducts() {
        return toPublicResponses(productoRepository
                .findTop10ByActivoTrueAndTotalResenasGreaterThanEqualOrderByPromedioCalificacionDescTotalResenasDesc(
                        MIN_RESENAS_TOP_CALIFICADOS));
    }

    /**
     * Retrieves the ten most-reviewed active products.
     * <p>
     * The result is cached.
     *
     * @return public product details ordered by review count
     */
    @Cacheable(value = "topResenados", unless = "#result == null or #result.isEmpty()")
    public List<ProductoPublicoResponse> getTopReviewedProducts() {
        return toPublicResponses(productoRepository.findTop10ByActivoTrueOrderByTotalResenasDesc());
    }

    /**
     * Retrieves the admin view of a product.
     *
     * <p>Unlike public queries, this also returns inactive products.</p>
     *
     * @param id product ID
     * @return the product's admin details
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    public ProductoAdminResponse getAdminProductById(Long id) {
        return toAdminResponse(findProductById(id));
    }

    /**
     * Creates a new product.
     *
     * <p>Normalizes the SKU by removing spaces and converting it to uppercase,
     * checks that it is unique, resolves the category if one is provided, and
     * generates a slug from the name and SKU using {@link SlugUtil}.</p>
     *
     * <p>The database's {@code unique} constraint guarantees SKU uniqueness.
     * A duplicate caused by concurrent requests is also reported as a
     * {@link SkuDuplicadoException}.</p>
     *
     * @param request product details
     * @return the created product's public details
     * @throws SkuDuplicadoException if a product with that SKU already exists
     * @throws CategoriaNoEncontradaException if the specified category does not exist
     */
    @Transactional
    public ProductoPublicoResponse createProduct(ProductoRequest request) {
        String sku = normalizeSku(request.sku());

        if (productoRepository.existsBySku(sku)) {
            throw new SkuDuplicadoException(sku);
        }

        Categoria categoria = resolveCategory(request.categoriaId());
        Producto nuevo = ProductoMappers.toEntity(request, categoria);
        nuevo.setSku(sku);
        nuevo.setSlug(SlugUtil.slugify(request.nombre() + " " + sku));

        try {
            return toPublicResponse(productoRepository.saveAndFlush(nuevo));
        } catch (DataIntegrityViolationException e) {
            throw new SkuDuplicadoException(sku);
        }
    }

    /**
     * Updates an existing product.
     *
     * <p>Normalizes the SKU, checks that it is not assigned to another product,
     * resolves the category if one is provided, and applies the changes. A
     * {@code flush} detects integrity violations and version conflicts within
     * the operation.</p>
     *
     * @param request updated product details
     * @param idProducto ID of the product to update
     * @return the updated product's public details
     * @throws ProductoNoEncontradoException if the product does not exist
     * @throws SkuDuplicadoException if another product already uses the SKU
     * @throws CategoriaNoEncontradaException if the specified category does not exist
     * @throws ProductoModificadoException if another user modified the product concurrently
     */
    @EvictTopProductos
    @Transactional
    public ProductoPublicoResponse updateProduct(ProductoRequest request, Long idProducto) {
        Producto producto = findProductById(idProducto);
        String sku = normalizeSku(request.sku());

        if (productoRepository.existsBySkuAndIdNot(sku, idProducto)) {
            throw new SkuDuplicadoException(sku);
        }

        Categoria categoria = resolveCategory(request.categoriaId());

        ProductoMappers.actualizarEntidad(producto, request, categoria);
        producto.setSku(sku);

        try {
            productoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new SkuDuplicadoException(sku);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ProductoModificadoException(idProducto);
        }

        return toPublicResponse(producto);
    }

    /**
     * Deletes a product and its image.
     *
     * <p>If the product has associated virtual codes or the database rejects
     * deletion because of other references, the product and its image are kept.
     * Cart entries containing the product are removed first so they do not
     * prevent deletion.</p>
     *
     * <p>The image is deleted from the bucket only after the transaction commits.</p>
     *
     * @param idProducto ID of the product to delete
     * @throws ProductoNoEncontradoException if the product does not exist
     * @throws ProductoConDependenciasException if the product has codes, orders, or other references
     */
    @EvictTopProductos
    @Transactional
    public void deleteProduct(Long idProducto) {
        Producto producto = findProductById(idProducto);

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
     * Deactivates a product without deleting its record.
     *
     * <p>The product no longer appears in public queries, but its codes, orders,
     * and image are kept. This is an alternative to {@link #deleteProduct(Long)}
     * when the product has dependencies. The change is persisted when the
     * transaction commits.</p>
     *
     * @param idProducto ID of the product to deactivate
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    @EvictTopProductos
    @Transactional
    public void deactivateProduct(Long idProducto) {
        findProductById(idProducto).setActivo(false);
    }

    /**
     * Uploads or replaces a product's image.
     *
     * <p>Uploads the new image to the product folder and stores its key. The
     * previous image is deleted from the bucket only after the transaction commits.</p>
     *
     * @param idProducto product ID
     * @param file image to upload
     * @return the product's admin details with the updated image
     * @throws ProductoNoEncontradoException if the product does not exist
     * @throws IOException if the file cannot be read
     */
    @Transactional
    public ProductoAdminResponse uploadProductImage(Long idProducto, MultipartFile file) throws IOException {
        Producto producto = findProductById(idProducto);

        String keyAnterior = producto.getImagenKey();
        producto.setImagenKey(storageService.upload(file, StorageService.StorageFolder.PRODUCTS));

        storageService.deleteAfterCommit(keyAnterior);
        return toAdminResponse(producto);
    }

    /**
     * Deletes a product's image.
     *
     * <p>Sets the key to {@code null} and deletes the file from the bucket only
     * after the transaction commits. If the product has no image, nothing happens.</p>
     *
     * @param idProducto product ID
     * @return the product's admin details without an image
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    @Transactional
    public ProductoAdminResponse removeProductImage(Long idProducto) {
        Producto producto = findProductById(idProducto);

        String key = producto.getImagenKey();
        producto.setImagenKey(null);
        storageService.deleteAfterCommit(key);

        return toAdminResponse(producto);
    }

    /**
     * Finds a product by ID, whether active or inactive.
     *
     * @param id product ID
     * @return the product
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    private Producto findProductById(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    /**
     * Resolves a category by its ID.
     *
     * @param categoriaId category ID, or {@code null} if the product has no category
     * @return the category, or {@code null} if the ID is {@code null}
     * @throws CategoriaNoEncontradaException if the ID is not {@code null} and no category exists
     */
    private Categoria resolveCategory(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    }

    /**
     * Removes whitespace and converts the SKU to uppercase for consistent comparisons.
     *
     * @param sku input SKU
     * @return normalized SKU
     */
    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Records a product view and converts it to its public representation.
     *
     * <p>The view count is incremented in the database and in the entity so the
     * response contains the updated value.</p>
     *
     * @param producto product whose view is being recorded
     * @return the product's public details with the updated view count
     */
    private ProductoPublicoResponse recordViewAndConvert(Producto producto) {
        productoRepository.incrementarVistas(producto.getId());
        producto.setVistas(producto.getVistas() + 1);
        return toPublicResponse(producto);
    }

    /**
     * Calculates the stock for a single product.
     *
     * @param producto product whose stock is being checked
     * @return number of available codes
     */
    private int getProductStock(Producto producto) {
        return (int) codigoVirtualRepository
                .countByProductoIdAndEstado(producto.getId(), EstadoCodigo.DISPONIBLE);
    }

    /**
     * Calculates the stock for multiple products in a single query.
     *
     * @param productos products whose stock is being checked
     * @return a map of product IDs to available stock; products with no available
     *         codes are omitted, and the map is empty if no products are provided
     */
    private Map<Long, Integer> getStockByProductId(Collection<Producto> productos) {
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
     * Converts a product to its public representation and retrieves its stock.
     *
     * @param producto entity to convert
     * @return public details with the image URL and current stock
     */
    private ProductoPublicoResponse toPublicResponse(Producto producto) {
        return toPublicResponse(producto, getProductStock(producto));
    }

    /**
     * Converts a product to its public representation using the provided stock.
     *
     * @param producto entity to convert
     * @param stock the product's available stock
     * @return public details with the image URL and specified stock
     */
    private ProductoPublicoResponse toPublicResponse(Producto producto, int stock) {
        return ProductoMappers.toPublicoResponse(
                producto, storageService.getPublicUrl(producto.getImagenKey()), stock);
    }

    /**
     * Converts a product to its admin representation.
     *
     * @param producto entity to convert
     * @return admin details with the image URL and current stock
     */
    private ProductoAdminResponse toAdminResponse(Producto producto) {
        return ProductoMappers.toAdminResponse(
                producto, storageService.getPublicUrl(producto.getImagenKey()), getProductStock(producto));
    }

    /**
     * Converts a page of products to public responses.
     *
     * <p>The stock for the entire page is retrieved in a single query. Products
     * with no available codes are reported with stock {@code 0}.</p>
     *
     * @param pagina page of entities to convert
     * @return a page containing each product's public details
     */
    private Page<ProductoPublicoResponse> toPublicPage(Page<Producto> pagina) {
        Map<Long, Integer> stock = getStockByProductId(pagina.getContent());
        return pagina.map(p -> toPublicResponse(p, stock.getOrDefault(p.getId(), 0)));
    }

    /**
     * Converts a list of products to public responses.
     *
     * <p>The stock for all products is retrieved in a single query. Products
     * with no available codes are reported with stock {@code 0}.</p>
     *
     * <p>The list is built as an {@link ArrayList} instead of using {@code .toList()}
     * because these results are cached, and the unmodifiable list returned by
     * {@code .toList()} may be incompatible with the configured serializer.</p>
     *
     * @param productos entities to convert
     * @return a mutable list of public responses, or an empty list if there are no products
     */
    private List<ProductoPublicoResponse> toPublicResponses(List<Producto> productos) {
        Map<Long, Integer> stock = getStockByProductId(productos);

        return productos.stream()
                .map(p -> toPublicResponse(p, stock.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
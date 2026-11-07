package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.cache.cacheEvict.EvictTopProductos;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.AdminProductResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.ProductRequest;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.CategoriaNoEncontradaException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoConDependenciasException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoModificadoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.SkuDuplicadoException;
import com.ecommerce.FerreViky.mapper.product.ProductMapper;
import com.ecommerce.FerreViky.models.Category;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.repository.CartItemRepository;
import com.ecommerce.FerreViky.repository.CategoryRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
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
 * {@link CodeStatus#AVAILABLE} state.
 */
@Service
@AllArgsConstructor
public class ProductService {

    private static final int MIN_REVIEWS_FOR_TOP_RATED = 3;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final VirtualCodeRepository virtualCodeRepository;
    private final CartItemRepository cartProductRepository;
    private final StorageService storageService;

    /**
     * Retrieves active products in paginated form.
     *
     * @param pageable pagination and sorting settings
     * @return a page of active products with their public details
     */
    public Page<PublicProductResponse> getAllProducts(Pageable pageable) {
        return toPublicPage(productRepository.findByActiveTrue(pageable));
    }

    /**
     * Retrieves an active product by ID and records a view.
     *
     * @param id product ID
     * @return the product's public details
     * @throws ProductoNoEncontradoException if the product does not exist or is inactive
     */
    public PublicProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .filter(Product::getActive)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
        return recordViewAndConvert(product);
    }

    /**
     * Retrieves an active product by its slug and records a view.
     *
     * @param slug the product's human-readable identifier
     * @return the product's public details
     * @throws ProductoNoEncontradoException if the product does not exist or is inactive
     */
    public PublicProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .filter(Product::getActive)
                .orElseThrow(() -> new ProductoNoEncontradoException(slug));
        return recordViewAndConvert(product);
    }

    /**
     * Searches active products by free text, with optional platform and
     * category filters.
     *
     * <p>Criteria are combined using {@link Specification}. Filters with a
     * {@code null} value are omitted from the search.</p>
     *
     * @param text free text to search for
     * @param platform platform to filter by
     * @param categoryId category ID to filter by
     * @param pageable pagination and sorting settings
     * @return a page of active products matching the criteria
     */
    public Page<PublicProductResponse> searchProducts(String text, String platform, Long categoryId, Pageable pageable) {
        Specification<Product> spec = ProductoSpecification.activeOnly()
                .and(ProductoSpecification.byText(text))
                .and(ProductoSpecification.withPlatform(platform))
                .and(ProductoSpecification.withCategory(categoryId));

        return toPublicPage(productRepository.findAll(spec, pageable));
    }

    /**
     * Retrieves the distinct platforms listed in the catalog.
     *
     * @return a list of unique platforms
     */
    public List<String> getPlatforms() {
        return productRepository.findDistinctPlatforms();
    }

    /**
     * Retrieves all categories in summary format.
     *
     * @return categories sorted by name
     */
    public List<CategoryOptionResponse> getCategories() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(c -> new CategoryOptionResponse(c.getId(), c.getName()))
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
    public List<PublicProductResponse> getTopViewedProducts() {
        return toPublicResponses(productRepository.findTop10ByActiveTrueOrderByViewsDesc());
    }

    /**
     * Retrieves the ten highest-rated active products with at least
     * {@value #MIN_REVIEWS_FOR_TOP_RATED} reviews.
     * <p>
     * The result is cached.
     *
     * @return public product details ordered by rating and review count
     */
    @Cacheable(value = "topCalificados", unless = "#result == null or #result.isEmpty()")
    public List<PublicProductResponse> getTopRatedProducts() {
        return toPublicResponses(productRepository
                .findTop10ByActiveTrueAndTotalReviewsGreaterThanEqualOrderByAverageRatingDescTotalReviewsDesc(
                        MIN_REVIEWS_FOR_TOP_RATED));
    }

    /**
     * Retrieves the ten most-reviewed active products.
     * <p>
     * The result is cached.
     *
     * @return public product details ordered by review count
     */
    @Cacheable(value = "topResenados", unless = "#result == null or #result.isEmpty()")
    public List<PublicProductResponse> getTopReviewedProducts() {
        return toPublicResponses(productRepository.findTop10ByActiveTrueOrderByTotalReviewsDesc());
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
    public AdminProductResponse getAdminProductById(Long id) {
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
    public PublicProductResponse createProduct(ProductRequest request) {
        String sku = normalizeSku(request.sku());

        if (productRepository.existsBySku(sku)) {
            throw new SkuDuplicadoException(sku);
        }

        Category category = resolveCategory(request.categoryId());
        Product newProduct = ProductMapper.toEntity(request, category);
        newProduct.setSku(sku);
        newProduct.setSlug(SlugUtil.slugify(request.name() + " " + sku));

        try {
            return toPublicResponse(productRepository.saveAndFlush(newProduct));
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
     * @param productId ID of the product to update
     * @return the updated product's public details
     * @throws ProductoNoEncontradoException if the product does not exist
     * @throws SkuDuplicadoException if another product already uses the SKU
     * @throws CategoriaNoEncontradaException if the specified category does not exist
     * @throws ProductoModificadoException if another user modified the product concurrently
     */
    @EvictTopProductos
    @Transactional
    public PublicProductResponse updateProduct(ProductRequest request, Long productId) {
        Product product = findProductById(productId);
        String sku = normalizeSku(request.sku());

        if (productRepository.existsBySkuAndIdNot(sku, productId)) {
            throw new SkuDuplicadoException(sku);
        }

        Category category = resolveCategory(request.categoryId());

        ProductMapper.updateEntity(product, request, category);
        product.setSku(sku);

        try {
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new SkuDuplicadoException(sku);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ProductoModificadoException(productId);
        }

        return toPublicResponse(product);
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
     * @param productId ID of the product to delete
     * @throws ProductoNoEncontradoException if the product does not exist
     * @throws ProductoConDependenciasException if the product has codes, orders, or other references
     */
    @EvictTopProductos
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = findProductById(productId);

        if (virtualCodeRepository.existsByProductId(productId)) {
            throw new ProductoConDependenciasException(productId);
        }

        String imageKey = product.getImageKey();

        try {
            cartProductRepository.deleteAllByProductId(productId);
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProductoConDependenciasException(productId);
        }

        storageService.deleteAfterCommit(imageKey);
    }

    /**
     * Deactivates a product without deleting its record.
     *
     * <p>The product no longer appears in public queries, but its codes, orders,
     * and image are kept. This is an alternative to {@link #deleteProduct(Long)}
     * when the product has dependencies. The change is persisted when the
     * transaction commits.</p>
     *
     * @param productId ID of the product to deactivate
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    @EvictTopProductos
    @Transactional
    public void deactivateProduct(Long productId) {
        findProductById(productId).setActive(false);
    }

    /**
     * Uploads or replaces a product's image.
     *
     * <p>Uploads the new image to the product folder and stores its key. The
     * previous image is deleted from the bucket only after the transaction commits.</p>
     *
     * @param productId product ID
     * @param file image to upload
     * @return the product's admin details with the updated image
     * @throws ProductoNoEncontradoException if the product does not exist
     * @throws IOException if the file cannot be read
     */
    @Transactional
    public AdminProductResponse uploadProductImage(Long productId, MultipartFile file) throws IOException {
        Product product = findProductById(productId);

        String previousImageKey = product.getImageKey();
        product.setImageKey(storageService.upload(file, StorageService.StorageFolder.PRODUCTS));

        storageService.deleteAfterCommit(previousImageKey);
        return toAdminResponse(product);
    }

    /**
     * Deletes a product's image.
     *
     * <p>Sets the key to {@code null} and deletes the file from the bucket only
     * after the transaction commits. If the product has no image, nothing happens.</p>
     *
     * @param productId product ID
     * @return the product's admin details without an image
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    @Transactional
    public AdminProductResponse removeProductImage(Long productId) {
        Product product = findProductById(productId);

        String key = product.getImageKey();
        product.setImageKey(null);
        storageService.deleteAfterCommit(key);

        return toAdminResponse(product);
    }

    /**
     * Finds a product by ID, whether active or inactive.
     *
     * @param id product ID
     * @return the product
     * @throws ProductoNoEncontradoException if the product does not exist
     */
    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    /**
     * Resolves a category by its ID.
     *
     * @param categoryId category ID, or {@code null} if the product has no category
     * @return the category, or {@code null} if the ID is {@code null}
     * @throws CategoriaNoEncontradaException if the ID is not {@code null} and no category exists
     */
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoryId));
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
     * @param product product whose view is being recorded
     * @return the product's public details with the updated view count
     */
    private PublicProductResponse recordViewAndConvert(Product product) {
        productRepository.incrementViews(product.getId());
        product.setViews(product.getViews() + 1);
        return toPublicResponse(product);
    }

    /**
     * Calculates the stock for a single product.
     *
     * @param product product whose stock is being checked
     * @return number of available codes
     */
    private int getProductStock(Product product) {
        return (int) virtualCodeRepository
                .countByProductIdAndStatus(product.getId(), CodeStatus.AVAILABLE);
    }

    /**
     * Calculates the stock for multiple products in a single query.
     *
     * @param products products whose stock is being checked
     * @return a map of product IDs to available stock; products with no available
     *         codes are omitted, and the map is empty if no products are provided
     */
    private Map<Long, Integer> getStockByProductId(Collection<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Integer> stock = new HashMap<>();
        virtualCodeRepository.findStockByProductIdsAndStatus(ids, CodeStatus.AVAILABLE)
                .forEach(s -> stock.put(s.getProductId(), s.getTotal().intValue()));
        return stock;
    }

    /**
     * Converts a product to its public representation and retrieves its stock.
     *
     * @param product entity to convert
     * @return public details with the image URL and current stock
     */
    private PublicProductResponse toPublicResponse(Product product) {
        return toPublicResponse(product, getProductStock(product));
    }

    /**
     * Converts a product to its public representation using the provided stock.
     *
     * @param product entity to convert
     * @param stock the product's available stock
     * @return public details with the image URL and specified stock
     */
    private PublicProductResponse toPublicResponse(Product product, int stock) {
        return ProductMapper.toPublicResponse(
                product, storageService.getPublicUrl(product.getImageKey()), stock);
    }

    /**
     * Converts a product to its admin representation.
     *
     * @param product entity to convert
     * @return admin details with the image URL and current stock
     */
    private AdminProductResponse toAdminResponse(Product product) {
        return ProductMapper.toAdminResponse(
                product, storageService.getPublicUrl(product.getImageKey()), getProductStock(product));
    }

    /**
     * Converts a page of products to public responses.
     *
     * <p>The stock for the entire page is retrieved in a single query. Products
     * with no available codes are reported with stock {@code 0}.</p>
     *
     * @param page page of entities to convert
     * @return a page containing each product's public details
     */
    private Page<PublicProductResponse> toPublicPage(Page<Product> page) {
        Map<Long, Integer> stock = getStockByProductId(page.getContent());
        return page.map(p -> toPublicResponse(p, stock.getOrDefault(p.getId(), 0)));
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
     * @param products entities to convert
     * @return a mutable list of public responses, or an empty list if there are no products
     */
    private List<PublicProductResponse> toPublicResponses(List<Product> products) {
        Map<Long, Integer> stock = getStockByProductId(products);

        return products.stream()
                .map(p -> toPublicResponse(p, stock.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
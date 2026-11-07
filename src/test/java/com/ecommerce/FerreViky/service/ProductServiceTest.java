package com.ecommerce.FerreViky.service;


import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.ProductRequest;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.AdminProductResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.models.Category;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.repository.CartItemRepository;
import com.ecommerce.FerreViky.repository.CategoryRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import com.ecommerce.FerreViky.utils.SlugUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static com.ecommerce.FerreViky.utils.Fakes.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Unit tests")
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private VirtualCodeRepository virtualCodeRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private ProductService productService;

    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }


    //  getProductById / getProductBySlug
    @Test
    @DisplayName("When getting a product by id that does not exist, it throws ProductoNoEncontradoException")
    void getProductById_notFound_throwsProductNotFound() {
        // Arrange
        Product p = generarProductoFake();

        when(productRepository.findById(p.getId())).thenReturn(Optional.empty());
        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getProductById(p.getId()));
    }

    @Test
    @DisplayName("When getting an inactive product by id, it throws ProductoNoEncontradoException")
    void getProductById_inactive_throwsProductNotFound() {
        // Arrange
        Product p = generarProductoFake(producto -> {
            producto.setActive(false);
        });

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getProductById(p.getId()));
    }

    @Test
    @DisplayName("When getting a product by slug that does not exist, it throws ProductoNoEncontradoException")
    void getProductBySlug_notFound_throwsProductNotFound() {
        // Arrange
        Product p = generarProductoFake();

        when(productRepository.findBySlug(p.getSlug())).thenReturn(Optional.empty());
        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getProductBySlug(p.getSlug()));
    }

    @Test
    @DisplayName("When getting an inactive product by slug, it throws ProductoNoEncontradoException")
    void getProductBySlug_inactive_throwsProductNotFound() {
        // Arrange
        Product p = generarProductoFake(producto -> {
            producto.setActive(false);
        });

        // Act + Assert
        when(productRepository.findBySlug(p.getSlug())).thenReturn(Optional.of(p));
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.getProductBySlug(p.getSlug()));
    }

    //  getAllProducts / searchProducts
    @Test
    @DisplayName("When getting all products, each one receives its own stock")
    void getAllProducts_withProducts_assignsStockToEachProduct() {
        // Arrange
        Product p1 = generarProductoFake(p -> { p.setId(1L); p.setImageKey("productos/uno.png"); });
        Product p2 = generarProductoFake(p -> { p.setId(2L); p.setImageKey("productos/dos.png"); });
        Page<Product> pagina = new PageImpl<>(List.of(p1, p2), pageable, 2);

        when(productRepository.findByActiveTrue(pageable)).thenReturn(pagina);
        when(virtualCodeRepository.findStockByProductIdsAndStatus(List.of(1L, 2L), CodeStatus.AVAILABLE))
                .thenReturn(List.of(stockFake(1L, 5L), stockFake(2L, 3L)));
        when(storageService.getPublicUrl("productos/uno.png")).thenReturn("http://img.com/uno.png");
        when(storageService.getPublicUrl("productos/dos.png")).thenReturn("http://img.com/dos.png");

        // Act
        Page<PublicProductResponse> resultado = productService.getAllProducts(pageable);

        // Assert
        assertEquals(2, resultado.getContent().size());
        assertEquals(esperadoPublico(p1, "http://img.com/uno.png", 5), resultado.getContent().get(0));
        assertEquals(esperadoPublico(p2, "http://img.com/dos.png", 3), resultado.getContent().get(1));
    }

    @Test
    @DisplayName("When a product has no available codes, its stock is reported as zero")
    void getAllProducts_productWithoutAvailableCodes_stockIsZero() {
        // Arrange
        Product conStock = generarProductoFake(p -> { p.setId(1L); p.setImageKey("productos/uno.png"); });
        Product sinStock = generarProductoFake(p -> { p.setId(2L); p.setImageKey("productos/dos.png"); });
        Page<Product> pagina = new PageImpl<>(List.of(conStock, sinStock), pageable, 2);

        when(productRepository.findByActiveTrue(pageable)).thenReturn(pagina);
        when(virtualCodeRepository.findStockByProductIdsAndStatus(List.of(1L, 2L), CodeStatus.AVAILABLE))
                .thenReturn(List.of(stockFake(1L, 5L)));   // product 2 does not appear
        when(storageService.getPublicUrl("productos/uno.png")).thenReturn("http://img.com/uno.png");
        when(storageService.getPublicUrl("productos/dos.png")).thenReturn("http://img.com/dos.png");

        // Act
        Page<PublicProductResponse> resultado = productService.getAllProducts(pageable);

        // Assert
        assertEquals(esperadoPublico(conStock, "http://img.com/uno.png", 5), resultado.getContent().get(0));
        assertEquals(esperadoPublico(sinStock, "http://img.com/dos.png", 0), resultado.getContent().get(1));
    }

    @Test
    @DisplayName("When getting an empty page, the code repository is not queried for stock")
    void getAllProducts_emptyPage_doesNotQueryStock() {
        // Arrange
        Page<Product> vacia = new PageImpl<>(List.of(), pageable, 0);
        when(productRepository.findByActiveTrue(pageable)).thenReturn(vacia);

        // Act
        Page<PublicProductResponse> resultado = productService.getAllProducts(pageable);

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("When searching with filters, the service returns the page provided by the repository")
    void searchProducts_withFilters_returnsRepositoryPage() {
        // Arrange
        Product p = generarProductoFake();
        String url = "http://img.com/abc.png";
        Page<Product> pagina = new PageImpl<>(List.of(p), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(pagina);
        when(virtualCodeRepository.findStockByProductIdsAndStatus(List.of(p.getId()), CodeStatus.AVAILABLE))
                .thenReturn(List.of(stockFake(p.getId(), 4L)));
        when(storageService.getPublicUrl(p.getImageKey())).thenReturn(url);

        // Act
        Page<PublicProductResponse> resultado =
                productService.searchProducts("steam", "Steam", 1L, pageable);

        // Assert
        assertEquals(1, resultado.getContent().size());
        assertEquals(esperadoPublico(p, url, 4), resultado.getContent().get(0));
        verify(productRepository).findAll(any(Specification.class), eq(pageable));
    }

    //  getPlatforms / getCategories
    @Test
    @DisplayName("When getting platforms, it returns the distinct platforms from the repository")
    void getPlatforms_returnsRepositoryList() {
        // Arrange
        List<String> plataformas = List.of("Xbox", "PlayStation");
        when(productRepository.findDistinctPlatforms()).thenReturn(plataformas);

        // Act
        List<String> resultado = productService.getPlatforms();

        // Assert
        assertEquals(plataformas, resultado);
        verify(productRepository).findDistinctPlatforms();
    }

    @Test
    @DisplayName("When getting categories, each one is mapped with its id and name")
    void getCategories_mapsIdAndName() {
        // Arrange
        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("Gift Cards");

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("Videojuegos");

        when(categoryRepository.findAll(Sort.by("name"))).thenReturn(List.of(c1, c2));

        // Act
        List<ProductDTO.CategoryOptionResponse> resultado = productService.getCategories();

        // Assert
        assertEquals(List.of(
                new ProductDTO.CategoryOptionResponse(1L, "Gift Cards"),
                new ProductDTO.CategoryOptionResponse(2L, "Videojuegos")
        ), resultado);
    }

    //  getAdminProductById
    @Test
    @DisplayName("When an admin gets a product, it is returned even if inactive")
    void getAdminProductById_exists_returnsEvenIfInactive() {
        // Arrange
        Product p = generarProductoFake(producto -> producto.setActive(false));
        String url = "http://img.com/abc.png";

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImageKey())).thenReturn(url);
        when(virtualCodeRepository.countByProductIdAndStatus(p.getId(), CodeStatus.AVAILABLE))
                .thenReturn(3L);

        // Act
        AdminProductResponse resultado = productService.getAdminProductById(p.getId());

        // Assert
        assertNotNull(resultado);
        assertEquals(p.getId(), resultado.id());
        assertEquals(3, resultado.stock());
        assertEquals(url, resultado.imageUrl());
    }

    @Test
    @DisplayName("When an admin gets a product that does not exist, it throws ProductoNoEncontradoException")
    void getAdminProductById_notFound_throwsProductNotFound() {
        // Arrange
        Product p = generarProductoFake(producto -> producto.setActive(false));

        // Act
        when(productRepository.findById(p.getId())).thenReturn(Optional.empty());

        // Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getAdminProductById(p.getId()));
    }

    //  createProduct
    @Test
    @DisplayName("When creating a product without a category, the category repository is not queried")
    void createProduct_withoutCategory_doesNotQueryCategoryRepository() {
        ProductRequest request = generarRequestFake();
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(request);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("When creating a product with a nonexistent category, it throws CategoriaNoEncontradaException")
    void createProduct_categoryNotFound_throwsCategoryNotFound() {
        // Arrange
        ProductRequest conCategoria = generarRequestFake(b -> b.categoryId = 5L);
        when(categoryRepository.findById(5L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CategoriaNoEncontradaException.class,
                () -> productService.createProduct(conCategoria));
        verify(productRepository, never()).saveAndFlush(any());
        // Verifies that nobody ever talked to this repository
    }

    @Test
    @DisplayName("When creating a product, the slug is generated from the name and the normalized SKU")
    void createProduct_generatesSlugFromNameAndSku() {
        ProductRequest request = generarRequestFake(b -> b.sku = "  steam-mx-200 ");
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        assertEquals("STEAM-MX-200", captor.getValue().getSku());
        assertEquals(SlugUtil.slugify(request.name() + " STEAM-MX-200"), captor.getValue().getSlug());
    }

    @Test
    @DisplayName("When creating a product with an existing SKU, it throws SkuDuplicadoException")
    void createProduct_skuExists_throwsDuplicateSku() {
        when(productRepository.existsBySku("STEAM-MX-200")).thenReturn(true);

        assertThrows(SkuDuplicadoException.class, () -> productService.createProduct(generarRequestFake()));
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When creating a product and the database rejects it for integrity, it throws SkuDuplicadoException")
    void createProduct_dataIntegrity_throwsDuplicateSku() {
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(SkuDuplicadoException.class, () -> productService.createProduct(generarRequestFake()));
    }

    //  updateProduct
    @Test
    @DisplayName("When updating a product that does not exist, it throws ProductoNoEncontradoException")
    void updateProduct_notFound_throwsProductNotFound() {
        // Arrange
        ProductRequest request = generarRequestFake();
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.updateProduct(request, 1L));
        verify(productRepository, never()).flush();
    }

    @Test
    @DisplayName("When updating a product with a nonexistent category, it throws CategoriaNoEncontradaException")
    void updateProduct_categoryNotFound_throwsCategoryNotFound() {
        // Arrange
        Product p = generarProductoFake();
        ProductRequest request = generarRequestFake(b -> b.categoryId = 99L);
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CategoriaNoEncontradaException.class,
                () -> productService.updateProduct(request, p.getId()));
        verify(productRepository, never()).flush();
    }

    @Test
    @DisplayName("When updating an existing product, its fields are updated and flush is called")
    void updateProduct_exists_updatesFieldsAndFlushes() {
        // Arrange
        Product p = generarProductoFake();
        Category nueva = new Category();
        nueva.setId(7L);
        nueva.setName("Streaming");

        ProductRequest request = generarRequestFake(b -> {
            b.name = "Netflix Gift Card $300 MXN";
            b.salePrice = new BigDecimal("315.00");
            b.categoryId = 7L;
        });

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(nueva));

        // Act
        productService.updateProduct(request, p.getId());

        // Assert
        assertEquals("Netflix Gift Card $300 MXN", p.getName());
        assertEquals(new BigDecimal("315.00"), p.getSalePrice());
        assertEquals(nueva, p.getCategory());
        verify(productRepository).flush();
    }

    @Test
    @DisplayName("When getting an existing active product by id, it returns its public data with image and stock")
    void getProductById_existsAndActive_returnsPublicProduct() {
        Product p = generarProductoFake();
        String url = "http://img.com/abc.png";
        PublicProductResponse esperado = esperadoPublico(p, url, 5, b -> b.views = 1L);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImageKey())).thenReturn(url);
        when(virtualCodeRepository.countByProductIdAndStatus(p.getId(), CodeStatus.AVAILABLE)).thenReturn(5L);

        PublicProductResponse resultado = productService.getProductById(p.getId());

        assertEquals(esperado, resultado);
        verify(productRepository).incrementViews(p.getId());
    }

    @Test
    @DisplayName("When getting an existing active product by slug, it returns its public data")
    void getProductBySlug_existsAndActive_returnsPublicProduct() {
        Product p = generarProductoFake();
        String url = "http://img.com/abc.png";
        PublicProductResponse esperado = esperadoPublico(p, url, 7, b -> b.views = 1L);

        when(productRepository.findBySlug(p.getSlug())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImageKey())).thenReturn(url);
        when(virtualCodeRepository.countByProductIdAndStatus(p.getId(), CodeStatus.AVAILABLE)).thenReturn(7L);

        PublicProductResponse resultado = productService.getProductBySlug(p.getSlug());

        assertEquals(esperado, resultado);
        verify(productRepository).incrementViews(p.getId());
    }

    //  deleteProduct
    @Test
    @DisplayName("When deleting a product that does not exist, it throws ProductoNoEncontradoException")
    void deleteProduct_notFound_throwsProductNotFound() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.deleteProduct(1L));
        verify(productRepository, never()).delete((Product) any());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("When deleting an existing product, it is removed and its image deletion is scheduled")
    void deleteProduct_exists_deletesAndSchedulesImageDeletion() {
        // Arrange
        Product p = generarProductoFake(producto -> producto.setImageKey("productos/borrar.png"));
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productService.deleteProduct(p.getId());

        // Assert
        // Verifies that the methods were called in a specific order
        InOrder orden = inOrder(cartItemRepository, productRepository, storageService);
        orden.verify(cartItemRepository).deleteAllByProductId(p.getId());
        orden.verify(productRepository).delete(p);
        orden.verify(productRepository).flush();
        orden.verify(storageService).deleteAfterCommit("productos/borrar.png");

        // Requires this exact order: CART CLEANUP -> DELETE -> FLUSH -> IMAGE DELETION
    }


    @Test
    @DisplayName("When deleting a product with associated codes, it throws ProductoConDependenciasException without touching the DB or the image")
    void deleteProduct_withCodes_throwsProductWithDependencies() {
        // Arrange
        Product p = generarProductoFake();
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(virtualCodeRepository.existsByProductId(p.getId())).thenReturn(true);

        // Act + Assert
        assertThrows(ProductoConDependenciasException.class,
                () -> productService.deleteProduct(p.getId()));
        verify(productRepository, never()).delete((Product) any());
        verifyNoInteractions(storageService);
    }

    //  deactivateProduct
    @Test
    @DisplayName("When deactivating an existing product, its active status changes to false")
    void deactivateProduct_exists_setsActiveToFalse() {
        // Arrange
        Product p = generarProductoFake();   // active = true
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productService.deactivateProduct(p.getId());

        // Assert
        assertFalse(p.getActive());
    }

    @Test
    @DisplayName("When deactivating a product that does not exist, it throws ProductoNoEncontradoException")
    void deactivateProduct_notFound_throwsProductNotFound() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.deactivateProduct(1L));
    }


    //  NEW TESTS 

    //  createProduct (new)
    @Test
    @DisplayName("When creating a product with an existing category, it is assigned to the saved product")
    void createProduct_withCategory_assignsCategory() {
        // Arrange
        Category category = new Category();
        category.setId(5L);
        category.setName("Gift Cards");
        ProductRequest request = generarRequestFake(b -> b.categoryId = 5L);

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        productService.createProduct(request);

        // Assert
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        assertEquals(category, captor.getValue().getCategory());
    }

    //  updateProduct (new)
    @Test
    @DisplayName("When updating a product with a SKU used by another product, it throws SkuDuplicadoException without flushing")
    void updateProduct_skuUsedByAnotherProduct_throwsDuplicateSku() {
        // Arrange
        Product p = generarProductoFake();
        ProductRequest request = generarRequestFake();
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(productRepository.existsBySkuAndIdNot("STEAM-MX-200", p.getId())).thenReturn(true);

        // Act + Assert
        assertThrows(SkuDuplicadoException.class,
                () -> productService.updateProduct(request, p.getId()));
        verify(productRepository, never()).flush();
    }

    @Test
    @DisplayName("When updating a product, the SKU is normalized before being saved")
    void updateProduct_normalizesSku() {
        // Arrange
        Product p = generarProductoFake();
        ProductRequest request = generarRequestFake(b -> b.sku = "  abc-123 ");
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productService.updateProduct(request, p.getId());

        // Assert
        assertEquals("ABC-123", p.getSku());
        verify(productRepository).existsBySkuAndIdNot("ABC-123", p.getId());
    }

    @Test
    @DisplayName("When updating a product and the database rejects it for integrity, it throws SkuDuplicadoException")
    void updateProduct_dataIntegrity_throwsDuplicateSku() {
        // Arrange
        Product p = generarProductoFake();
        ProductRequest request = generarRequestFake();
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        doThrow(new DataIntegrityViolationException("dup")).when(productRepository).flush();

        // Act + Assert
        assertThrows(SkuDuplicadoException.class,
                () -> productService.updateProduct(request, p.getId()));
    }

    @Test
    @DisplayName("When updating a product modified by another user, it throws ProductoModificadoException")
    void updateProduct_optimisticLock_throwsProductModified() {
        // Arrange
        Product p = generarProductoFake();
        ProductRequest request = generarRequestFake();
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        doThrow(new ObjectOptimisticLockingFailureException(Product.class, p.getId()))
                .when(productRepository).flush();

        // Act + Assert
        assertThrows(ProductoModificadoException.class,
                () -> productService.updateProduct(request, p.getId()));
    }

    //  deleteProduct (new)
    @Test
    @DisplayName("When deleting a product and the database rejects it for references, it throws ProductoConDependenciasException and keeps the image")
    void deleteProduct_dataIntegrity_throwsProductWithDependencies() {
        // Arrange
        Product p = generarProductoFake();
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        doThrow(new DataIntegrityViolationException("fk")).when(productRepository).flush();

        // Act + Assert
        assertThrows(ProductoConDependenciasException.class,
                () -> productService.deleteProduct(p.getId()));
        verifyNoInteractions(storageService);
    }

    //  images
    @Test
    @DisplayName("When uploading an image, the new key is saved and deletion of the previous one is scheduled")
    void uploadProductImage_exists_replacesKeyAndSchedulesPreviousDeletion() throws IOException {
        // Arrange
        Product p = generarProductoFake(producto -> producto.setImageKey("productos/vieja.png"));
        MultipartFile archivo = mock(MultipartFile.class);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.upload(archivo, StorageService.StorageFolder.PRODUCTS)).thenReturn("productos/nueva.png");
        when(storageService.getPublicUrl("productos/nueva.png")).thenReturn("http://img.com/nueva.png");

        // Act
        AdminProductResponse resultado = productService.uploadProductImage(p.getId(), archivo);

        // Assert
        assertEquals("productos/nueva.png", p.getImageKey());
        assertEquals("http://img.com/nueva.png", resultado.imageUrl());
        verify(storageService).deleteAfterCommit("productos/vieja.png");
    }

    @Test
    @DisplayName("When uploading an image for a product that does not exist, it throws ProductoNoEncontradoException without touching storage")
    void uploadProductImage_notFound_throwsProductNotFound() {
        // Arrange
        MultipartFile archivo = mock(MultipartFile.class);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.uploadProductImage(1L, archivo));
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("When removing the image, the key is set to null and file deletion is scheduled")
    void removeProductImage_exists_setsKeyToNullAndSchedulesDeletion() {
        // Arrange
        Product p = generarProductoFake(producto -> producto.setImageKey("productos/vieja.png"));
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        AdminProductResponse resultado = productService.removeProductImage(p.getId());

        // Assert
        assertNull(p.getImageKey());
        assertNull(resultado.imageUrl());
        verify(storageService).deleteAfterCommit("productos/vieja.png");
    }

    @Test
    @DisplayName("When removing the image of a product that does not exist, it throws ProductoNoEncontradoException")
    void removeProductImage_notFound_throwsProductNotFound() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.removeProductImage(1L));
        verifyNoInteractions(storageService);
    }

    //  top products
    @Test
    @DisplayName("When getting the most viewed products with none available, it returns an empty list without querying stock")
    void getTopViewedProducts_noProducts_emptyListWithoutQueryingStock() {
        // Arrange
        when(productRepository.findTop10ByActiveTrueOrderByViewsDesc()).thenReturn(List.of());

        // Act
        List<PublicProductResponse> resultado = productService.getTopViewedProducts();

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("When getting the most viewed products, each one receives its stock")
    void getTopViewedProducts_withProducts_assignsStock() {
        // Arrange
        Product p = generarProductoFake(x -> x.setId(1L));
        String url = "http://img.com/abc.png";
        when(productRepository.findTop10ByActiveTrueOrderByViewsDesc()).thenReturn(List.of(p));
        when(virtualCodeRepository.findStockByProductIdsAndStatus(List.of(1L), CodeStatus.AVAILABLE))
                .thenReturn(List.of(stockFake(1L, 9L)));
        when(storageService.getPublicUrl(p.getImageKey())).thenReturn(url);

        // Act
        List<PublicProductResponse> resultado = productService.getTopViewedProducts();

        // Assert
        assertEquals(List.of(esperadoPublico(p, url, 9)), resultado);
    }

    @Test
    @DisplayName("When getting the top rated products, a minimum of 3 reviews is required")
    void getTopRatedProducts_requiresMinimumOfThreeReviews() {
        // Arrange
        when(productRepository
                .findTop10ByActiveTrueAndTotalReviewsGreaterThanEqualOrderByAverageRatingDescTotalReviewsDesc(3))
                .thenReturn(List.of());

        // Act
        List<PublicProductResponse> resultado = productService.getTopRatedProducts();

        // Assert
        assertTrue(resultado.isEmpty());
        verify(productRepository)
                .findTop10ByActiveTrueAndTotalReviewsGreaterThanEqualOrderByAverageRatingDescTotalReviewsDesc(3);
    }

    @Test
    @DisplayName("When getting the most reviewed products, it returns a mutable ArrayList (cache-compatible)")
    void getTopReviewedProducts_returnsMutableList() {
        // Arrange
        Product p = generarProductoFake(x -> x.setId(1L));
        when(productRepository.findTop10ByActiveTrueOrderByTotalReviewsDesc()).thenReturn(List.of(p));
        when(virtualCodeRepository.findStockByProductIdsAndStatus(List.of(1L), CodeStatus.AVAILABLE))
                .thenReturn(List.of());

        // Act
        List<PublicProductResponse> resultado = productService.getTopReviewedProducts();

        // Assert
        assertEquals(1, resultado.size());
        assertDoesNotThrow(() -> resultado.add(resultado.get(0)));
    }

}
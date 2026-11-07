package com.ecommerce.FerreViky.service;


import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoProductoRepository;
import com.ecommerce.FerreViky.repository.CategoriaRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
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
    private ProductoRepository productoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private CodigoVirtualRepository codigoVirtualRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private CarritoProductoRepository carritoProductoRepository;

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
        Producto p = generarProductoFake();

        when(productoRepository.findById(p.getId())).thenReturn(Optional.empty());
        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getProductById(p.getId()));
    }

    @Test
    @DisplayName("When getting an inactive product by id, it throws ProductoNoEncontradoException")
    void getProductById_inactive_throwsProductNotFound() {
        // Arrange
        Producto p = generarProductoFake(producto -> {
            producto.setActivo(false);
        });

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getProductById(p.getId()));
    }

    @Test
    @DisplayName("When getting a product by slug that does not exist, it throws ProductoNoEncontradoException")
    void getProductBySlug_notFound_throwsProductNotFound() {
        // Arrange
        Producto p = generarProductoFake();

        when(productoRepository.findBySlug(p.getSlug())).thenReturn(Optional.empty());
        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getProductBySlug(p.getSlug()));
    }

    @Test
    @DisplayName("When getting an inactive product by slug, it throws ProductoNoEncontradoException")
    void getProductBySlug_inactive_throwsProductNotFound() {
        // Arrange
        Producto p = generarProductoFake(producto -> {
            producto.setActivo(false);
        });

        // Act + Assert
        when(productoRepository.findBySlug(p.getSlug())).thenReturn(Optional.of(p));
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.getProductBySlug(p.getSlug()));
    }

    //  getAllProducts / searchProducts
    @Test
    @DisplayName("When getting all products, each one receives its own stock")
    void getAllProducts_withProducts_assignsStockToEachProduct() {
        // Arrange
        Producto p1 = generarProductoFake(p -> { p.setId(1L); p.setImagenKey("productos/uno.png"); });
        Producto p2 = generarProductoFake(p -> { p.setId(2L); p.setImagenKey("productos/dos.png"); });
        Page<Producto> pagina = new PageImpl<>(List.of(p1, p2), pageable, 2);

        when(productoRepository.findByActivoTrue(pageable)).thenReturn(pagina);
        when(codigoVirtualRepository.contarPorProducto(List.of(1L, 2L), EstadoCodigo.DISPONIBLE))
                .thenReturn(List.of(stockFake(1L, 5L), stockFake(2L, 3L)));
        when(storageService.getPublicUrl("productos/uno.png")).thenReturn("http://img.com/uno.png");
        when(storageService.getPublicUrl("productos/dos.png")).thenReturn("http://img.com/dos.png");

        // Act
        Page<ProductoPublicoResponse> resultado = productService.getAllProducts(pageable);

        // Assert
        assertEquals(2, resultado.getContent().size());
        assertEquals(esperadoPublico(p1, "http://img.com/uno.png", 5), resultado.getContent().get(0));
        assertEquals(esperadoPublico(p2, "http://img.com/dos.png", 3), resultado.getContent().get(1));
    }

    @Test
    @DisplayName("When a product has no available codes, its stock is reported as zero")
    void getAllProducts_productWithoutAvailableCodes_stockIsZero() {
        // Arrange
        Producto conStock = generarProductoFake(p -> { p.setId(1L); p.setImagenKey("productos/uno.png"); });
        Producto sinStock = generarProductoFake(p -> { p.setId(2L); p.setImagenKey("productos/dos.png"); });
        Page<Producto> pagina = new PageImpl<>(List.of(conStock, sinStock), pageable, 2);

        when(productoRepository.findByActivoTrue(pageable)).thenReturn(pagina);
        when(codigoVirtualRepository.contarPorProducto(List.of(1L, 2L), EstadoCodigo.DISPONIBLE))
                .thenReturn(List.of(stockFake(1L, 5L)));   // product 2 does not appear
        when(storageService.getPublicUrl("productos/uno.png")).thenReturn("http://img.com/uno.png");
        when(storageService.getPublicUrl("productos/dos.png")).thenReturn("http://img.com/dos.png");

        // Act
        Page<ProductoPublicoResponse> resultado = productService.getAllProducts(pageable);

        // Assert
        assertEquals(esperadoPublico(conStock, "http://img.com/uno.png", 5), resultado.getContent().get(0));
        assertEquals(esperadoPublico(sinStock, "http://img.com/dos.png", 0), resultado.getContent().get(1));
    }

    @Test
    @DisplayName("When getting an empty page, the code repository is not queried for stock")
    void getAllProducts_emptyPage_doesNotQueryStock() {
        // Arrange
        Page<Producto> vacia = new PageImpl<>(List.of(), pageable, 0);
        when(productoRepository.findByActivoTrue(pageable)).thenReturn(vacia);

        // Act
        Page<ProductoPublicoResponse> resultado = productService.getAllProducts(pageable);

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(codigoVirtualRepository);
    }

    @Test
    @DisplayName("When searching with filters, the service returns the page provided by the repository")
    void searchProducts_withFilters_returnsRepositoryPage() {
        // Arrange
        Producto p = generarProductoFake();
        String url = "http://img.com/abc.png";
        Page<Producto> pagina = new PageImpl<>(List.of(p), pageable, 1);

        when(productoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(pagina);
        when(codigoVirtualRepository.contarPorProducto(List.of(p.getId()), EstadoCodigo.DISPONIBLE))
                .thenReturn(List.of(stockFake(p.getId(), 4L)));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);

        // Act
        Page<ProductoPublicoResponse> resultado =
                productService.searchProducts("steam", "Steam", 1L, pageable);

        // Assert
        assertEquals(1, resultado.getContent().size());
        assertEquals(esperadoPublico(p, url, 4), resultado.getContent().get(0));
        verify(productoRepository).findAll(any(Specification.class), eq(pageable));
    }

    //  getPlatforms / getCategories
    @Test
    @DisplayName("When getting platforms, it returns the distinct platforms from the repository")
    void getPlatforms_returnsRepositoryList() {
        // Arrange
        List<String> plataformas = List.of("Xbox", "PlayStation");
        when(productoRepository.findPlataformasDistintas()).thenReturn(plataformas);

        // Act
        List<String> resultado = productService.getPlatforms();

        // Assert
        assertEquals(plataformas, resultado);
        verify(productoRepository).findPlataformasDistintas();
    }

    @Test
    @DisplayName("When getting categories, each one is mapped with its id and name")
    void getCategories_mapsIdAndName() {
        // Arrange
        Categoria c1 = new Categoria();
        c1.setId(1L);
        c1.setNombre("Gift Cards");

        Categoria c2 = new Categoria();
        c2.setId(2L);
        c2.setNombre("Videojuegos");

        when(categoriaRepository.findAll(Sort.by("nombre"))).thenReturn(List.of(c1, c2));

        // Act
        List<ProductoDTO.CategoriaOpcionResponse> resultado = productService.getCategories();

        // Assert
        assertEquals(List.of(
                new ProductoDTO.CategoriaOpcionResponse(1L, "Gift Cards"),
                new ProductoDTO.CategoriaOpcionResponse(2L, "Videojuegos")
        ), resultado);
    }

    //  getAdminProductById
    @Test
    @DisplayName("When an admin gets a product, it is returned even if inactive")
    void getAdminProductById_exists_returnsEvenIfInactive() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setActivo(false));
        String url = "http://img.com/abc.png";

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), EstadoCodigo.DISPONIBLE))
                .thenReturn(3L);

        // Act
        ProductoAdminResponse resultado = productService.getAdminProductById(p.getId());

        // Assert
        assertNotNull(resultado);
        assertEquals(p.getId(), resultado.id());
        assertEquals(3, resultado.stock());
        assertEquals(url, resultado.imagenUrl());
    }

    @Test
    @DisplayName("When an admin gets a product that does not exist, it throws ProductoNoEncontradoException")
    void getAdminProductById_notFound_throwsProductNotFound() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setActivo(false));

        // Act
        when(productoRepository.findById(p.getId())).thenReturn(Optional.empty());

        // Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productService.getAdminProductById(p.getId()));
    }

    //  createProduct
    @Test
    @DisplayName("When creating a product without a category, the category repository is not queried")
    void createProduct_withoutCategory_doesNotQueryCategoryRepository() {
        ProductoRequest request = generarRequestFake();
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(request);

        verifyNoInteractions(categoriaRepository);
    }

    @Test
    @DisplayName("When creating a product with a nonexistent category, it throws CategoriaNoEncontradaException")
    void createProduct_categoryNotFound_throwsCategoryNotFound() {
        // Arrange
        ProductoRequest conCategoria = generarRequestFake(b -> b.categoriaId = 5L);
        when(categoriaRepository.findById(5L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CategoriaNoEncontradaException.class,
                () -> productService.createProduct(conCategoria));
        verify(productoRepository, never()).saveAndFlush(any());
        // Verifies that nobody ever talked to this repository
    }

    @Test
    @DisplayName("When creating a product, the slug is generated from the name and the normalized SKU")
    void createProduct_generatesSlugFromNameAndSku() {
        ProductoRequest request = generarRequestFake(b -> b.sku = "  steam-mx-200 ");
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).saveAndFlush(captor.capture());
        assertEquals("STEAM-MX-200", captor.getValue().getSku());
        assertEquals(SlugUtil.slugify(request.nombre() + " STEAM-MX-200"), captor.getValue().getSlug());
    }

    @Test
    @DisplayName("When creating a product with an existing SKU, it throws SkuDuplicadoException")
    void createProduct_skuExists_throwsDuplicateSku() {
        when(productoRepository.existsBySku("STEAM-MX-200")).thenReturn(true);

        assertThrows(SkuDuplicadoException.class, () -> productService.createProduct(generarRequestFake()));
        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When creating a product and the database rejects it for integrity, it throws SkuDuplicadoException")
    void createProduct_dataIntegrity_throwsDuplicateSku() {
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(SkuDuplicadoException.class, () -> productService.createProduct(generarRequestFake()));
    }

    //  updateProduct
    @Test
    @DisplayName("When updating a product that does not exist, it throws ProductoNoEncontradoException")
    void updateProduct_notFound_throwsProductNotFound() {
        // Arrange
        ProductoRequest request = generarRequestFake();
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.updateProduct(request, 1L));
        verify(productoRepository, never()).flush();
    }

    @Test
    @DisplayName("When updating a product with a nonexistent category, it throws CategoriaNoEncontradaException")
    void updateProduct_categoryNotFound_throwsCategoryNotFound() {
        // Arrange
        Producto p = generarProductoFake();
        ProductoRequest request = generarRequestFake(b -> b.categoriaId = 99L);
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CategoriaNoEncontradaException.class,
                () -> productService.updateProduct(request, p.getId()));
        verify(productoRepository, never()).flush();
    }

    @Test
    @DisplayName("When updating an existing product, its fields are updated and flush is called")
    void updateProduct_exists_updatesFieldsAndFlushes() {
        // Arrange
        Producto p = generarProductoFake();
        Categoria nueva = new Categoria();
        nueva.setId(7L);
        nueva.setNombre("Streaming");

        ProductoRequest request = generarRequestFake(b -> {
            b.nombre = "Netflix Gift Card $300 MXN";
            b.precioVenta = new BigDecimal("315.00");
            b.categoriaId = 7L;
        });

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(categoriaRepository.findById(7L)).thenReturn(Optional.of(nueva));

        // Act
        productService.updateProduct(request, p.getId());

        // Assert
        assertEquals("Netflix Gift Card $300 MXN", p.getNombre());
        assertEquals(new BigDecimal("315.00"), p.getPrecioVenta());
        assertEquals(nueva, p.getCategoria());
        verify(productoRepository).flush();
    }

    @Test
    @DisplayName("When getting an existing active product by id, it returns its public data with image and stock")
    void getProductById_existsAndActive_returnsPublicProduct() {
        Producto p = generarProductoFake();
        String url = "http://img.com/abc.png";
        ProductoPublicoResponse esperado = esperadoPublico(p, url, 5, b -> b.vistas = 1L);

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), EstadoCodigo.DISPONIBLE)).thenReturn(5L);

        ProductoPublicoResponse resultado = productService.getProductById(p.getId());

        assertEquals(esperado, resultado);
        verify(productoRepository).incrementarVistas(p.getId());
    }

    @Test
    @DisplayName("When getting an existing active product by slug, it returns its public data")
    void getProductBySlug_existsAndActive_returnsPublicProduct() {
        Producto p = generarProductoFake();
        String url = "http://img.com/abc.png";
        ProductoPublicoResponse esperado = esperadoPublico(p, url, 7, b -> b.vistas = 1L);

        when(productoRepository.findBySlug(p.getSlug())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), EstadoCodigo.DISPONIBLE)).thenReturn(7L);

        ProductoPublicoResponse resultado = productService.getProductBySlug(p.getSlug());

        assertEquals(esperado, resultado);
        verify(productoRepository).incrementarVistas(p.getId());
    }

    //  deleteProduct
    @Test
    @DisplayName("When deleting a product that does not exist, it throws ProductoNoEncontradoException")
    void deleteProduct_notFound_throwsProductNotFound() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.deleteProduct(1L));
        verify(productoRepository, never()).delete((Producto) any());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("When deleting an existing product, it is removed and its image deletion is scheduled")
    void deleteProduct_exists_deletesAndSchedulesImageDeletion() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setImagenKey("productos/borrar.png"));
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productService.deleteProduct(p.getId());

        // Assert
        // Verifies that the methods were called in a specific order
        InOrder orden = inOrder(carritoProductoRepository, productoRepository, storageService);
        orden.verify(carritoProductoRepository).eliminarPorProductoId(p.getId());
        orden.verify(productoRepository).delete(p);
        orden.verify(productoRepository).flush();
        orden.verify(storageService).deleteAfterCommit("productos/borrar.png");

        // Requires this exact order: CART CLEANUP -> DELETE -> FLUSH -> IMAGE DELETION
    }


    @Test
    @DisplayName("When deleting a product with associated codes, it throws ProductoConDependenciasException without touching the DB or the image")
    void deleteProduct_withCodes_throwsProductWithDependencies() {
        // Arrange
        Producto p = generarProductoFake();
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(codigoVirtualRepository.existsByProductoId(p.getId())).thenReturn(true);

        // Act + Assert
        assertThrows(ProductoConDependenciasException.class,
                () -> productService.deleteProduct(p.getId()));
        verify(productoRepository, never()).delete((Producto) any());
        verifyNoInteractions(storageService);
    }

    //  deactivateProduct
    @Test
    @DisplayName("When deactivating an existing product, its active status changes to false")
    void deactivateProduct_exists_setsActiveToFalse() {
        // Arrange
        Producto p = generarProductoFake();   // activo = true
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productService.deactivateProduct(p.getId());

        // Assert
        assertFalse(p.getActivo());
    }

    @Test
    @DisplayName("When deactivating a product that does not exist, it throws ProductoNoEncontradoException")
    void deactivateProduct_notFound_throwsProductNotFound() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

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
        Categoria categoria = new Categoria();
        categoria.setId(5L);
        categoria.setNombre("Gift Cards");
        ProductoRequest request = generarRequestFake(b -> b.categoriaId = 5L);

        when(categoriaRepository.findById(5L)).thenReturn(Optional.of(categoria));
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        productService.createProduct(request);

        // Assert
        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).saveAndFlush(captor.capture());
        assertEquals(categoria, captor.getValue().getCategoria());
    }

    //  updateProduct (new)
    @Test
    @DisplayName("When updating a product with a SKU used by another product, it throws SkuDuplicadoException without flushing")
    void updateProduct_skuUsedByAnotherProduct_throwsDuplicateSku() {
        // Arrange
        Producto p = generarProductoFake();
        ProductoRequest request = generarRequestFake();
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(productoRepository.existsBySkuAndIdNot("STEAM-MX-200", p.getId())).thenReturn(true);

        // Act + Assert
        assertThrows(SkuDuplicadoException.class,
                () -> productService.updateProduct(request, p.getId()));
        verify(productoRepository, never()).flush();
    }

    @Test
    @DisplayName("When updating a product, the SKU is normalized before being saved")
    void updateProduct_normalizesSku() {
        // Arrange
        Producto p = generarProductoFake();
        ProductoRequest request = generarRequestFake(b -> b.sku = "  abc-123 ");
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productService.updateProduct(request, p.getId());

        // Assert
        assertEquals("ABC-123", p.getSku());
        verify(productoRepository).existsBySkuAndIdNot("ABC-123", p.getId());
    }

    @Test
    @DisplayName("When updating a product and the database rejects it for integrity, it throws SkuDuplicadoException")
    void updateProduct_dataIntegrity_throwsDuplicateSku() {
        // Arrange
        Producto p = generarProductoFake();
        ProductoRequest request = generarRequestFake();
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        doThrow(new DataIntegrityViolationException("dup")).when(productoRepository).flush();

        // Act + Assert
        assertThrows(SkuDuplicadoException.class,
                () -> productService.updateProduct(request, p.getId()));
    }

    @Test
    @DisplayName("When updating a product modified by another user, it throws ProductoModificadoException")
    void updateProduct_optimisticLock_throwsProductModified() {
        // Arrange
        Producto p = generarProductoFake();
        ProductoRequest request = generarRequestFake();
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        doThrow(new ObjectOptimisticLockingFailureException(Producto.class, p.getId()))
                .when(productoRepository).flush();

        // Act + Assert
        assertThrows(ProductoModificadoException.class,
                () -> productService.updateProduct(request, p.getId()));
    }

    //  deleteProduct (new)
    @Test
    @DisplayName("When deleting a product and the database rejects it for references, it throws ProductoConDependenciasException and keeps the image")
    void deleteProduct_dataIntegrity_throwsProductWithDependencies() {
        // Arrange
        Producto p = generarProductoFake();
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        doThrow(new DataIntegrityViolationException("fk")).when(productoRepository).flush();

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
        Producto p = generarProductoFake(producto -> producto.setImagenKey("productos/vieja.png"));
        MultipartFile archivo = mock(MultipartFile.class);

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.upload(archivo, StorageService.StorageFolder.PRODUCTS)).thenReturn("productos/nueva.png");
        when(storageService.getPublicUrl("productos/nueva.png")).thenReturn("http://img.com/nueva.png");

        // Act
        ProductoAdminResponse resultado = productService.uploadProductImage(p.getId(), archivo);

        // Assert
        assertEquals("productos/nueva.png", p.getImagenKey());
        assertEquals("http://img.com/nueva.png", resultado.imagenUrl());
        verify(storageService).deleteAfterCommit("productos/vieja.png");
    }

    @Test
    @DisplayName("When uploading an image for a product that does not exist, it throws ProductoNoEncontradoException without touching storage")
    void uploadProductImage_notFound_throwsProductNotFound() {
        // Arrange
        MultipartFile archivo = mock(MultipartFile.class);
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productService.uploadProductImage(1L, archivo));
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("When removing the image, the key is set to null and file deletion is scheduled")
    void removeProductImage_exists_setsKeyToNullAndSchedulesDeletion() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setImagenKey("productos/vieja.png"));
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        ProductoAdminResponse resultado = productService.removeProductImage(p.getId());

        // Assert
        assertNull(p.getImagenKey());
        assertNull(resultado.imagenUrl());
        verify(storageService).deleteAfterCommit("productos/vieja.png");
    }

    @Test
    @DisplayName("When removing the image of a product that does not exist, it throws ProductoNoEncontradoException")
    void removeProductImage_notFound_throwsProductNotFound() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

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
        when(productoRepository.findTop10ByActivoTrueOrderByVistasDesc()).thenReturn(List.of());

        // Act
        List<ProductoPublicoResponse> resultado = productService.getTopViewedProducts();

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(codigoVirtualRepository);
    }

    @Test
    @DisplayName("When getting the most viewed products, each one receives its stock")
    void getTopViewedProducts_withProducts_assignsStock() {
        // Arrange
        Producto p = generarProductoFake(x -> x.setId(1L));
        String url = "http://img.com/abc.png";
        when(productoRepository.findTop10ByActivoTrueOrderByVistasDesc()).thenReturn(List.of(p));
        when(codigoVirtualRepository.contarPorProducto(List.of(1L), EstadoCodigo.DISPONIBLE))
                .thenReturn(List.of(stockFake(1L, 9L)));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);

        // Act
        List<ProductoPublicoResponse> resultado = productService.getTopViewedProducts();

        // Assert
        assertEquals(List.of(esperadoPublico(p, url, 9)), resultado);
    }

    @Test
    @DisplayName("When getting the top rated products, a minimum of 3 reviews is required")
    void getTopRatedProducts_requiresMinimumOfThreeReviews() {
        // Arrange
        when(productoRepository
                .findTop10ByActivoTrueAndTotalResenasGreaterThanEqualOrderByPromedioCalificacionDescTotalResenasDesc(3))
                .thenReturn(List.of());

        // Act
        List<ProductoPublicoResponse> resultado = productService.getTopRatedProducts();

        // Assert
        assertTrue(resultado.isEmpty());
        verify(productoRepository)
                .findTop10ByActivoTrueAndTotalResenasGreaterThanEqualOrderByPromedioCalificacionDescTotalResenasDesc(3);
    }

    @Test
    @DisplayName("When getting the most reviewed products, it returns a mutable ArrayList (cache-compatible)")
    void getTopReviewedProducts_returnsMutableList() {
        // Arrange
        Producto p = generarProductoFake(x -> x.setId(1L));
        when(productoRepository.findTop10ByActivoTrueOrderByTotalResenasDesc()).thenReturn(List.of(p));
        when(codigoVirtualRepository.contarPorProducto(List.of(1L), EstadoCodigo.DISPONIBLE))
                .thenReturn(List.of());

        // Act
        List<ProductoPublicoResponse> resultado = productService.getTopReviewedProducts();

        // Assert
        assertEquals(1, resultado.size());
        assertDoesNotThrow(() -> resultado.add(resultado.get(0)));
    }

}
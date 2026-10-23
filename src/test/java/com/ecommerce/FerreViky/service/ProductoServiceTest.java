package com.ecommerce.FerreViky.service;


import com.ecommerce.FerreViky.dto.producto.ProductoDTO.*;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.models.CodigoVirtual;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoProductoRepository;
import com.ecommerce.FerreViky.repository.CategoriaRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static com.ecommerce.FerreViky.utils.Fakes.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoService - Pruebas unitarias")
class ProductoServiceTest {
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
    private ProductoService productoService;

    private Pageable pageable;

    private final Faker faker = new Faker();

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }


    @Test
    @DisplayName("Al buscar por id un producto existente y activo este devuelve sus datos publicos con imagen y stock")
    void obtenerProductoPorId_existeYActivo_devuelveProductoPublico() {
        // Arrange
        Producto p = generarProductoFake();
        String url = "http://img.com/abc.png";
        ProductoPublicoResponse esperado = esperadoPublico(p, url, 5);

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE)).thenReturn(5L);

        // Act
        ProductoPublicoResponse resultado = productoService.obtenerProductoPorId(p.getId());

        // Assert
        assertEquals(esperado, resultado);
        verify(productoRepository).findById(p.getId());
    }

    @Test
    @DisplayName("Al buscar por id un producto que no existe este lanza ProductoNoEncontradoException")
    void obtenerProductoPorId_noExiste_lanzaProductoNoEncontrado() {
        //Arrange
        Producto p = generarProductoFake();

        when(productoRepository.findById(p.getId())).thenReturn(Optional.empty());
        //Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productoService.obtenerProductoPorId(p.getId()));
    }

    @Test
    @DisplayName("Al buscar por id un producto inactivo este lanza ProductoNoEncontradoException")
    void obtenerProductoPorId_inactivo_lanzaProductoNoEncontrado() {
        //Arrange
        Producto p = generarProductoFake(producto -> {
            producto.setActivo(false);
        });

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        //Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productoService.obtenerProductoPorId(p.getId()));
    }

    @Test
    @DisplayName("Al buscar por slug un producto existente y activo este devuelve sus datos publicos")
    void obtenerProductoPorSlug_existeYActivo_devuelveProductoPublico() {
        // Arrange
        Producto p = generarProductoFake();
        String url = "http://img.com/abc.png";
        ProductoPublicoResponse esperado = esperadoPublico(p, url, 7);

        when(productoRepository.findBySlug(p.getSlug())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE)).thenReturn(7L);

        // Act
        ProductoPublicoResponse resultado = productoService.obtenerProductoPorSlug(p.getSlug());

        // Assert
        assertEquals(esperado, resultado);
        verify(productoRepository).findBySlug(p.getSlug());
    }

    @Test
    @DisplayName("Al buscar por slug un producto que no existe este lanza ProductoNoEncontradoException")
    void obtenerProductoPorSlug_noExiste_lanzaProductoNoEncontrado() {
        //Arrange
        Producto p = generarProductoFake();

        when(productoRepository.findBySlug(p.getSlug())).thenReturn(Optional.empty());
        //Act + Assert
        assertThrows(ProductoNoEncontradoException.class, () -> productoService.obtenerProductoPorSlug(p.getSlug()));
    }

    @Test
    @DisplayName("Al buscar por slug un producto inactivo este lanza ProductoNoEncontradoException")
    void obtenerProductoPorSlug_inactivo_lanzaProductoNoEncontrado() {
        //Arrange
        Producto p = generarProductoFake(producto -> {
            producto.setActivo(false);
        });

        //Act + Assert
        when(productoRepository.findBySlug(p.getSlug())).thenReturn(Optional.of(p));
        assertThrows(ProductoNoEncontradoException.class,
                () -> productoService.obtenerProductoPorSlug(p.getSlug()));
    }

    //  obtenerTodos / buscar 
    @Test
    @DisplayName("Al obtener todos los productos cada uno recibe el stock que le corresponde")
    void obtenerTodos_conProductos_asignaStockDeCadaProducto() {
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
        Page<ProductoPublicoResponse> resultado = productoService.obtenerTodos(pageable);

        // Assert
        assertEquals(2, resultado.getContent().size());
        assertEquals(esperadoPublico(p1, "http://img.com/uno.png", 5), resultado.getContent().get(0));
        assertEquals(esperadoPublico(p2, "http://img.com/dos.png", 3), resultado.getContent().get(1));
    }

    @Test
    @DisplayName("Al obtener un producto sin codigos disponibles su stock se reporta en cero")
    void obtenerTodos_productoSinCodigosDisponibles_stockEsCero() {
        // Arrange
        Producto conStock = generarProductoFake(p -> { p.setId(1L); p.setImagenKey("productos/uno.png"); });
        Producto sinStock = generarProductoFake(p -> { p.setId(2L); p.setImagenKey("productos/dos.png"); });
        Page<Producto> pagina = new PageImpl<>(List.of(conStock, sinStock), pageable, 2);

        when(productoRepository.findByActivoTrue(pageable)).thenReturn(pagina);
        when(codigoVirtualRepository.contarPorProducto(List.of(1L, 2L), EstadoCodigo.DISPONIBLE))
                .thenReturn(List.of(stockFake(1L, 5L)));   // el 2 no aparece
        when(storageService.getPublicUrl("productos/uno.png")).thenReturn("http://img.com/uno.png");
        when(storageService.getPublicUrl("productos/dos.png")).thenReturn("http://img.com/dos.png");

        // Act
        Page<ProductoPublicoResponse> resultado = productoService.obtenerTodos(pageable);

        // Assert
        assertEquals(esperadoPublico(conStock, "http://img.com/uno.png", 5), resultado.getContent().get(0));
        assertEquals(esperadoPublico(sinStock, "http://img.com/dos.png", 0), resultado.getContent().get(1));
    }

    @Test
    @DisplayName("Al obtener una pagina vacia no se consulta el stock en el repositorio de codigos")
    void obtenerTodos_paginaVacia_noConsultaStock() {
        // Arrange
        Page<Producto> vacia = new PageImpl<>(List.of(), pageable, 0);
        when(productoRepository.findByActivoTrue(pageable)).thenReturn(vacia);

        // Act
        Page<ProductoPublicoResponse> resultado = productoService.obtenerTodos(pageable);

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(codigoVirtualRepository);
    }

    @Test
    @DisplayName("Al buscar con filtros el servicio devuelve la pagina que entrega el repositorio")
    void buscar_conFiltros_devuelvePaginaDelRepositorio() {
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
                productoService.buscar("steam", "Steam", 1L, pageable);

        // Assert
        assertEquals(1, resultado.getContent().size());
        assertEquals(esperadoPublico(p, url, 4), resultado.getContent().get(0));
        verify(productoRepository).findAll(any(Specification.class), eq(pageable));
    }

    //  obtenerPlataformas / obtenerListaDeCategorias 
    @Test
    @DisplayName("Al obtener las plataformas devuelve la lista de plataformas distintas del repositorio")
    void obtenerPlataformas_devuelveListaDelRepositorio() {
        // Arrange
        List<String> plataformas = List.of("Xbox", "PlayStation");
        when(productoRepository.findPlataformasDistintas()).thenReturn(plataformas);

        // Act
        List<String> resultado = productoService.obtenerPlataformas();

        // Assert
        assertEquals(plataformas, resultado);
        verify(productoRepository).findPlataformasDistintas();
    }

    @Test
    @DisplayName("Al obtener la lista de categorias cada una se convierte con su id y su nombre")
    void obtenerListaDeCategorias_mapeaIdYNombre() {
        // Arrange
        Categoria c1 = new Categoria();
        c1.setId(1L);
        c1.setNombre("Gift Cards");

        Categoria c2 = new Categoria();
        c2.setId(2L);
        c2.setNombre("Videojuegos");

        when(categoriaRepository.findAll(Sort.by("nombre"))).thenReturn(List.of(c1, c2));

        // Act
        List<CategoriaOpcionResponse> resultado = productoService.obtenerListaDeCategorias();

        // Assert
        assertEquals(List.of(
                new CategoriaOpcionResponse(1L, "Gift Cards"),
                new CategoriaOpcionResponse(2L, "Videojuegos")
        ), resultado);
    }

    //  obtenerUnProducto (admin) 
    @Test
    @DisplayName("Al consultar un producto como administrador lo devuelve aunque este inactivo")
    void obtenerUnProducto_existe_devuelveAunqueEsteInactivo() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setActivo(false));
        String url = "http://img.com/abc.png";

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(storageService.getPublicUrl(p.getImagenKey())).thenReturn(url);
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), EstadoCodigo.DISPONIBLE))
                .thenReturn(3L);

        // Act
        ProductoAdminResponse resultado = productoService.obtenerUnProducto(p.getId());

        // Assert
        assertNotNull(resultado);
        assertEquals(p.getId(), resultado.id());
        assertEquals(3, resultado.stock());
        assertEquals(url, resultado.imagenUrl());
    }

    @Test
    @DisplayName("Al consultar como administrador un producto que no existe este lanza ProductoNoEncontradoException")
    void obtenerUnProducto_noExiste_lanzaProductoNoEncontrado() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setActivo(false));

        //Act
        when(productoRepository.findById(p.getId())).thenReturn(Optional.empty());

        //Assert
        assertThrows(ProductoNoEncontradoException.class,()->productoService.obtenerUnProducto(p.getId()));
    }

    @Test
    @DisplayName("Al crear un producto sin categoria no se consulta el repositorio de categorias")
    void crearProducto_sinCategoria_noConsultaCategoriaRepository() {
        // Arrange
        ProductoRequest request = generarRequestFake();
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        //Cuando alguien llame a esta funcion devuelve el producto que me mando jeje

        // Act
        productoService.crearProducto(request);

        // Assert
        verifyNoInteractions(categoriaRepository);
    }

    @Test
    @DisplayName("Al crear un producto con una categoria inexistente este lanza CategoriaNoEncontradaException")
    void crearProducto_categoriaNoExiste_lanzaCategoriaNoEncontrada() {
        // Arrange
        ProductoRequest conCategoria = generarRequestFake(b -> b.categoriaId = 5L);
        when(categoriaRepository.findById(5L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CategoriaNoEncontradaException.class,
                () -> productoService.crearProducto(conCategoria));
        verify(productoRepository, never()).save(any());
        //Verifica que a este repositorio nadie haya hablado con el
    }

    @Test
    @DisplayName("Al crear un producto con slug libre este se guarda con el slug base del nombre")
    void crearProducto_slugLibre_guardaConSlugBase() {
        // Arrange
        ProductoRequest request = generarRequestFake();
        when(productoRepository.existsBySlug("steam-gift-card-200-mxn")).thenReturn(false);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        productoService.crearProducto(request);

        // Assert
        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class); //Argumento real con el que se llamo al metodo Mock
        //Util cuando no somos nosotros quien creamos eso
        verify(productoRepository).save(captor.capture()); //Se llamo SAVE y encima me quedo lo que recibio
        assertEquals("steam-gift-card-200-mxn", captor.getValue().getSlug());
    }

    @Test
    @DisplayName("Al crear un producto cuyo slug ya existe se le agrega el sufijo 2")
    void crearProducto_slugOcupado_agregaSufijo2() {
        // Arrange
        ProductoRequest request = generarRequestFake();
        when(productoRepository.existsBySlug("steam-gift-card-200-mxn")).thenReturn(true);
        when(productoRepository.existsBySlug("steam-gift-card-200-mxn-2")).thenReturn(false);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        productoService.crearProducto(request);

        // Assert
        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        assertEquals("steam-gift-card-200-mxn-2", captor.getValue().getSlug());
    }

    @Test
    @DisplayName("Al crear un producto cuyo slug y sufijo 2 ya existen se le agrega el sufijo 3")
    void crearProducto_slugBaseYSufijo2Ocupados_agregaSufijo3() {
        // Arrange
        ProductoRequest request = generarRequestFake();
        when(productoRepository.existsBySlug("steam-gift-card-200-mxn")).thenReturn(true);
        when(productoRepository.existsBySlug("steam-gift-card-200-mxn-2")).thenReturn(true);
        when(productoRepository.existsBySlug("steam-gift-card-200-mxn-3")).thenReturn(false);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        productoService.crearProducto(request);

        // Assert
        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        assertEquals("steam-gift-card-200-mxn-3", captor.getValue().getSlug());
    }

    //  editarProducto 
    @Test
    @DisplayName("Al editar un producto que no existe este lanza ProductoNoEncontradoException")
    void editarProducto_noExiste_lanzaProductoNoEncontrado() {
        // Arrange
        ProductoRequest request = generarRequestFake();
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productoService.editarProducto(request, 1L));
        verify(productoRepository, never()).flush();
    }

    @Test
    @DisplayName("Al editar un producto con una categoria inexistente este lanza CategoriaNoEncontradaException")
    void editarProducto_categoriaNoExiste_lanzaCategoriaNoEncontrada() {
        // Arrange
        Producto p = generarProductoFake();
        ProductoRequest request = generarRequestFake(b -> b.categoriaId = 99L);
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CategoriaNoEncontradaException.class,
                () -> productoService.editarProducto(request, p.getId()));
        verify(productoRepository, never()).flush();
    }

    @Test
    @DisplayName("Al editar un producto existente se actualizan sus campos y se hace flush")
    void editarProducto_existe_actualizaCamposYHaceFlush() {
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
        productoService.editarProducto(request, p.getId());

        // Assert
        assertEquals("Netflix Gift Card $300 MXN", p.getNombre());
        assertEquals(new BigDecimal("315.00"), p.getPrecioVenta());
        assertEquals(nueva, p.getCategoria());
        verify(productoRepository).flush();
    }

    //  borrarProducto 
    @Test
    @DisplayName("Al borrar un producto que no existe este lanza ProductoNoEncontradoException")
    void borrarProducto_noExiste_lanzaProductoNoEncontrado() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productoService.borrarProducto(1L));
        verify(productoRepository, never()).delete((Producto) any());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("Al borrar un producto existente este se elimina y se programa el borrado de su imagen")
    void borrarProducto_existe_borraYProgramaBorradoDeImagen() {
        // Arrange
        Producto p = generarProductoFake(producto -> producto.setImagenKey("productos/borrar.png"));
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productoService.borrarProducto(p.getId());

        // Assert
        //Verifica que los metodos se llamaron en un Orden especial
        InOrder orden = inOrder(carritoProductoRepository, productoRepository, storageService);
        orden.verify(carritoProductoRepository).eliminarPorProductoId(p.getId());
        orden.verify(productoRepository).delete(p);
        orden.verify(productoRepository).flush();
        orden.verify(storageService).deleteAfterCommit("productos/borrar.png");

        //Exige que haya sido asi DELETE->FLUSH->Delete
    }


    @Test
    @DisplayName("Al borrar un producto con codigos asociados lanza ProductoConDependenciasException sin tocar la BD ni la imagen")
    void borrarProducto_conCodigos_lanzaProductoConDependencias() {
        // Arrange
        Producto p = generarProductoFake();
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(codigoVirtualRepository.existsByProductoId(p.getId())).thenReturn(true);

        // Act + Assert
        assertThrows(ProductoConDependenciasException.class,
                () -> productoService.borrarProducto(p.getId()));
        verify(productoRepository, never()).delete((Producto) any());
        verifyNoInteractions(storageService);
    }

    //  desactivarProducto 
    @Test
    @DisplayName("Al desactivar un producto existente este cambia su estado activo a false")
    void desactivarProducto_existe_poneActivoEnFalse() {
        // Arrange
        Producto p = generarProductoFake();   // activo = true
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act
        productoService.desactivarProducto(p.getId());

        // Assert
        assertFalse(p.getActivo());
    }

    @Test
    @DisplayName("Al desactivar un producto que no existe este lanza ProductoNoEncontradoException")
    void desactivarProducto_noExiste_lanzaProductoNoEncontrado() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> productoService.desactivarProducto(1L));
    }

}
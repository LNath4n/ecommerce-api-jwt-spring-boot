package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions;
import com.ecommerce.FerreViky.models.GrupoDeProductos;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.GrupoDeProductosRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoService - Pruebas unitarias")
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private GrupoDeProductosRepository grupoDeProductosRepository;

    @InjectMocks
    private ProductoService productoService;

    private Pageable pageable;

    private final Faker faker = new Faker();

    // Helpers

    private Producto crearProductoFake() {
        Producto p = new Producto();
        p.setId(faker.number().randomNumber());
        p.setCodigo(faker.code().ean8());
        p.setDescripcion(faker.commerce().productName());
        p.setMarca(faker.company().name());
        p.setUnidad("Pieza");
        p.setPrecioPublicoIva(BigDecimal.valueOf(faker.number().randomDouble(2, 10, 500)));
        p.setStock((int) faker.number().numberBetween(1L, 100L));
        return p;
    }

    private GrupoDeProductos crearGrupoFake() {
        GrupoDeProductos g = new GrupoDeProductos();
        g.setId(faker.number().randomNumber());
        g.setNombre(faker.commerce().department());
        g.setPrefijoClave(faker.bothify("??##"));
        g.setPalabrasComunes(faker.lorem().word());
        g.setEstilos(List.of());
        return g;
    }

    private ProductoRequest crearRequestFake(Long grupoId) {
        return new ProductoRequest(
                faker.code().ean8(),                         // codigo
                faker.bothify("??-###"),                     // clave
                faker.commerce().productName(),              // descripcion
                "MM00",                                      // margenMercado
                "2",                                         // caja
                "12",                                        // master
                "Pieza",                                     // unidad
                faker.code().ean13(),                        // ean
                BigDecimal.valueOf(300),                     // precioMayoreoIva
                BigDecimal.valueOf(250),                     // precioDistribuidorIva
                BigDecimal.valueOf(400),                     // precioPublicoIva
                faker.company().name(),                      // marca
                grupoId,                                     // grupoDeProductosId
                (int) faker.number().numberBetween(1L, 100L) // stock
        );
    }

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }

    //  Productos 

    @Test
    @DisplayName("obtenerTodos() → retorna página con los productos encontrados")
    public void deberiaRetornarPaginaDeProductos() {
        Page<Producto> paginaFalsa = new PageImpl<>(List.of(
                crearProductoFake(),
                crearProductoFake()
        ));

        when(productoRepository.findAllWithGrupo(pageable)).thenReturn(paginaFalsa);

        Page<ProductoPublicoResponse> resultado = productoService.obtenerTodos(pageable);

        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        verify(productoRepository).findAllWithGrupo(pageable);
    }

    @Test
    @DisplayName("obtenerProductoPorId() → retorna el producto cuando el ID existe")
    public void deberiaEncontrarElProducto() {
        Producto producto = crearProductoFake();

        when(productoRepository.findById(producto.getId())).thenReturn(Optional.of(producto));

        ProductoPublicoResponse resultado = productoService.obtenerProductoPorId(producto.getId());

        assertNotNull(resultado);
        assertEquals(producto.getId(), resultado.id());
        verify(productoRepository).findById(producto.getId());
    }

    @Test
    @DisplayName("obtenerProductoPorId() → lanza ProductoNoEncontradoException cuando el ID no existe")
    public void deberiaLanzarExcepcionCuandoProductoNoExiste() {
        Long idInexistente = 999L;

        when(productoRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThrows(
                ProductosExceptions.ProductoNoEncontradoException.class,
                () -> productoService.obtenerProductoPorId(idInexistente)
        );

        verify(productoRepository).findById(idInexistente);
    }

    //  Grupos de Productos 

    @Test
    @DisplayName("obtenerTodosLosGrupos() → retorna página con los grupos encontrados")
    public void deberiaRetornarPaginaDeGrupos() {
        List<GrupoDeProductos> grupos = List.of(crearGrupoFake(), crearGrupoFake());
        Page<GrupoDeProductos> paginaFalsa = new PageImpl<>(grupos);

        when(grupoDeProductosRepository.findAllPaged(pageable)).thenReturn(paginaFalsa);
        when(grupoDeProductosRepository.findWithEstilos(grupos)).thenReturn(grupos);

        Page<GruposDeProductosDTO.GrupoPublicoResponse> resultado = productoService.obtenerTodosLosGrupos(pageable);

        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        verify(grupoDeProductosRepository).findAllPaged(pageable);
        verify(grupoDeProductosRepository).findWithEstilos(grupos);
    }

    @Test
    @DisplayName("obtenerTodosLosGrupos() → retorna página vacía cuando no hay grupos registrados")
    public void deberiaRetornarPaginaVaciaDeGrupos() {
        when(grupoDeProductosRepository.findAllPaged(pageable)).thenReturn(Page.empty());
        when(grupoDeProductosRepository.findWithEstilos(List.of())).thenReturn(List.of());

        Page<GruposDeProductosDTO.GrupoPublicoResponse> resultado = productoService.obtenerTodosLosGrupos(pageable);

        assertNotNull(resultado);
        assertEquals(0, resultado.getTotalElements());
        verify(grupoDeProductosRepository).findAllPaged(pageable);
    }

    @Test
    @DisplayName("obtenerGrupoPorId() → retorna el grupo cuando el ID existe")
    public void deberiaEncontrarElGrupo() {
        GrupoDeProductos grupo = crearGrupoFake();

        when(grupoDeProductosRepository.findById(grupo.getId())).thenReturn(Optional.of(grupo));

        GruposDeProductosDTO.GrupoPublicoResponse resultado = productoService.obtenerGrupoPorId(grupo.getId());

        assertNotNull(resultado);
        assertEquals(grupo.getId(), resultado.id());
        assertEquals(grupo.getNombre(), resultado.nombre());
        verify(grupoDeProductosRepository).findById(grupo.getId());
    }

    @Test
    @DisplayName("obtenerGrupoPorId() → lanza ProductoNoEncontradoException cuando el grupo no existe")
    public void deberiaLanzarExcepcionCuandoGrupoNoExiste() {
        Long idInexistente = 999L;

        when(grupoDeProductosRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThrows(
                ProductosExceptions.ProductoNoEncontradoException.class,
                () -> productoService.obtenerGrupoPorId(idInexistente)
        );

        verify(grupoDeProductosRepository).findById(idInexistente);
    }

    //  crearProducto 

    @Test
    @DisplayName("crearProducto() → guarda el producto sin grupo cuando grupoDeProductosId es null")
    public void deberiaCrearProductoSinGrupo() {
        ProductoRequest request = crearRequestFake(null);

        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(1L); // simula el id generado por la BD
            return p;
        });

        ProductoPublicoResponse resultado = productoService.crearProducto(request);

        assertEquals(1L, resultado.id());
        assertEquals(request.codigo(), resultado.codigo());
        assertEquals(request.stock(), resultado.stock());
        verifyNoInteractions(grupoDeProductosRepository);
    }

    @Test
    @DisplayName("crearProducto() → asigna el grupo cuando el id existe")
    public void deberiaCrearProductoConGrupo() {
        GrupoDeProductos grupo = crearGrupoFake();
        ProductoRequest request = crearRequestFake(grupo.getId());

        when(grupoDeProductosRepository.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        productoService.crearProducto(request);

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        assertSame(grupo, captor.getValue().getGrupoDeProductos());
        assertEquals(request.clave(), captor.getValue().getClave());
    }

    @Test
    @DisplayName("crearProducto() → lanza GrupoNoEncontradoException y no guarda si el grupo no existe")
    public void deberiaLanzarExcepcionAlCrearConGrupoInexistente() {
        Long grupoInexistente = 999L;
        ProductoRequest request = crearRequestFake(grupoInexistente);

        when(grupoDeProductosRepository.findById(grupoInexistente)).thenReturn(Optional.empty());

        assertThrows(
                ProductosExceptions.GrupoNoEncontradoException.class,
                () -> productoService.crearProducto(request)
        );

        verify(productoRepository, never()).save(any());
    }

    //  editarProducto 

    @Test
    @DisplayName("editarProducto() → actualiza los campos, hace flush y retorna el producto")
    public void deberiaEditarProducto() {
        Producto existente = crearProductoFake();
        ProductoRequest request = crearRequestFake(null);

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

        ProductoPublicoResponse resultado = productoService.editarProducto(request, existente.getId());

        assertEquals(existente.getId(), resultado.id()); // el id no cambia
        assertEquals(request.codigo(), existente.getCodigo());
        assertEquals(request.clave(), existente.getClave());
        assertEquals(request.marca(), existente.getMarca());
        assertNull(existente.getGrupoDeProductos());
        verify(productoRepository).flush();
        verify(productoRepository, never()).save(any()); // managed: no hace falta save
    }

    @Test
    @DisplayName("editarProducto() → asigna el nuevo grupo cuando el id existe")
    public void deberiaEditarProductoConGrupo() {
        Producto existente = crearProductoFake();
        GrupoDeProductos grupo = crearGrupoFake();
        ProductoRequest request = crearRequestFake(grupo.getId());

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(grupoDeProductosRepository.findById(grupo.getId())).thenReturn(Optional.of(grupo));

        productoService.editarProducto(request, existente.getId());

        assertSame(grupo, existente.getGrupoDeProductos());
        verify(productoRepository).flush();
    }

    @Test
    @DisplayName("editarProducto() → lanza ProductoNoEncontradoException cuando el producto no existe")
    public void deberiaLanzarExcepcionAlEditarProductoInexistente() {
        Long idInexistente = 999L;

        when(productoRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThrows(
                ProductosExceptions.ProductoNoEncontradoException.class,
                () -> productoService.editarProducto(crearRequestFake(null), idInexistente)
        );

        verify(productoRepository, never()).flush();
    }

    @Test
    @DisplayName("editarProducto() → lanza GrupoNoEncontradoException y no modifica el producto")
    public void deberiaLanzarExcepcionAlEditarConGrupoInexistente() {
        Long grupoInexistente = 999L;
        Producto existente = crearProductoFake();
        String descripcionOriginal = existente.getDescripcion();

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(grupoDeProductosRepository.findById(grupoInexistente)).thenReturn(Optional.empty());

        assertThrows(
                ProductosExceptions.GrupoNoEncontradoException.class,
                () -> productoService.editarProducto(crearRequestFake(grupoInexistente), existente.getId())
        );

        assertEquals(descripcionOriginal, existente.getDescripcion());
        verify(productoRepository, never()).flush();
    }

    //  borrarProducto 

    @Test
    @DisplayName("borrarProducto() → elimina el producto cuando el id existe")
    public void deberiaBorrarProducto() {
        Producto existente = crearProductoFake();

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

        productoService.borrarProducto(existente.getId());

        verify(productoRepository).delete(existente);
    }

    @Test
    @DisplayName("borrarProducto() → lanza ProductoNoEncontradoException y no borra si no existe")
    public void deberiaLanzarExcepcionAlBorrarProductoInexistente() {
        Long idInexistente = 999L;

        when(productoRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThrows(
                ProductosExceptions.ProductoNoEncontradoException.class,
                () -> productoService.borrarProducto(idInexistente)
        );

        verify(productoRepository, never()).delete((Producto) any());
    }
}
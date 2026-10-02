package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
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
import static org.mockito.Mockito.verify;
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

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }

    // Productos

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

    // Grupos de Productos

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
}
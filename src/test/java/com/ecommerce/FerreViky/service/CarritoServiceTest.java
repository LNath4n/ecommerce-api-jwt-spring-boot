package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.carrito.CarritoDTO;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions;
import com.ecommerce.FerreViky.mapper.carrito.CarritoMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.models.CarritoProducto;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CarritoService - Pruebas unitarias")
class CarritoServiceTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private CarritoRepository carritoRepository;

    @InjectMocks
    private CarritoService carritoService;

    private final Faker faker = new Faker();


    private Producto generarProductoFake(int stock) {
        Producto p = new Producto();
        p.setId(faker.number().randomNumber());
        p.setCodigo(faker.code().ean8());
        p.setDescripcion(faker.commerce().productName());
        p.setMarca(faker.company().name());
        p.setUnidad("Pieza");
        p.setPrecioPublicoIva(BigDecimal.valueOf(faker.number().randomDouble(2, 10, 500)));
        p.setStock(stock);
        return p;
    }

    private Cliente generarClienteFake() {
        Cliente c = new Cliente();
        c.setId(faker.number().randomNumber());
        c.setEmail(faker.internet().safeEmailAddress());
        c.setPassword("");
        return c;
    }

    /** Crea un carrito con lista mutable (importante para los tests de agregar) */
    private Carrito generarCarritoVacioFake(Cliente cliente) {
        Carrito c = new Carrito();
        c.setId(faker.number().randomNumber());
        c.setCliente(cliente);
        c.setFechaCreacion(LocalDateTime.now());
        c.setProductos(new ArrayList<>());
        return c;
    }

    private CarritoProducto generarItemFake(Carrito carrito, Producto producto, int cantidad) {
        CarritoProducto cp = new CarritoProducto();
        cp.setId(faker.number().randomNumber());
        cp.setCarrito(carrito);
        cp.setProducto(producto);
        cp.setCantidad(cantidad);
        return cp;
    }

    // agregarOActualizar() — producto no existe

    @Test
    @DisplayName("agregarOActualizar() → lanza ProductoNoEncontradoException cuando el producto no existe")
    void agregarOActualizar_deberiaLanzarExcepcion_cuandoProductoNoExiste() {
        // Given
        Cliente cliente = generarClienteFake();
        AgregarCarrito dto = new AgregarCarrito(999L, 2);

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.empty());

        // When / Then
        assertThrows(ProductosExceptions.ProductoNoEncontradoException.class,
                () -> carritoService.agregarOActualizar(dto, cliente));

        verify(carritoRepository, never()).findByClienteConProductos(any());
        verify(carritoRepository, never()).save(any());
    }

    // agregarOActualizar() — validación de cantidad

    @Test
    @DisplayName("agregarOActualizar() → lanza CantidadNoValida cuando la cantidad es 0")
    void agregarOActualizar_deberiaLanzarExcepcion_cuandoCantidadEsCero() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10);
        AgregarCarrito dto = new AgregarCarrito(producto.getId(), 0);

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));

        // When / Then
        assertThrows(CarritoExceptions.CantidadNoValida.class,
                () -> carritoService.agregarOActualizar(dto, cliente));
    }

    @Test
    @DisplayName("agregarOActualizar() → lanza CantidadNoValida cuando la cantidad es negativa")
    void agregarOActualizar_deberiaLanzarExcepcion_cuandoCantidadEsNegativa() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10);
        AgregarCarrito dto = new AgregarCarrito(producto.getId(), -5);

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));

        // When / Then
        assertThrows(CarritoExceptions.CantidadNoValida.class,
                () -> carritoService.agregarOActualizar(dto, cliente));
    }

    @Test
    @DisplayName("agregarOActualizar() → lanza CantidadExcedidaException cuando la cantidad solicitada supera el stock")
    void agregarOActualizar_deberiaLanzarExcepcion_cuandoCantidadSuperaStock() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(5); // stock = 5
        AgregarCarrito dto = new AgregarCarrito(producto.getId(), 10); // pide 10

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));

        // When / Then
        assertThrows(CarritoExceptions.CantidadExcedidaException.class,
                () -> carritoService.agregarOActualizar(dto, cliente));
    }

    // agregarOActualizar() — camino feliz: producto nuevo en carrito

    @Test
    @DisplayName("agregarOActualizar() → agrega nuevo item al carrito cuando el producto no estaba antes")
    void agregarOActualizar_deberiaAgregarNuevoItem_cuandoProductoNoEstabaEnCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10);
        Carrito carrito = generarCarritoVacioFake(cliente);
        AgregarCarrito dto = new AgregarCarrito(producto.getId(), 3);

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));
        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(carrito)).thenReturn(carrito);

        // When
        carritoService.agregarOActualizar(dto, cliente);

        // Then
        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepository).save(captor.capture());

        Carrito carritoGuardado = captor.getValue();
        assertEquals(1, carritoGuardado.getProductos().size());
        CarritoProducto item = carritoGuardado.getProductos().get(0);
        assertEquals(producto.getId(), item.getProducto().getId());
        assertEquals(3, item.getCantidad());
    }


    // agregarOActualizar() — camino feliz: producto ya existe, actualiza cantidad

    @Test
    @DisplayName("agregarOActualizar() → suma la cantidad cuando el producto ya estaba en el carrito")
    void agregarOActualizar_deberiaActualizarCantidad_cuandoProductoYaEstabaEnCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(20); // stock = 20
        Carrito carrito = generarCarritoVacioFake(cliente);

        // El producto ya está en el carrito con cantidad 5
        CarritoProducto itemExistente = generarItemFake(carrito, producto, 5);
        carrito.setProductos(new ArrayList<>(List.of(itemExistente)));

        AgregarCarrito dto = new AgregarCarrito(producto.getId(), 3);

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));
        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(carrito)).thenReturn(carrito);

        // When
        carritoService.agregarOActualizar(dto, cliente);

        // Then: la cantidad debe haberse sumado
        assertEquals(8, itemExistente.getCantidad());
        verify(carritoRepository).save(carrito);
    }

    @Test
    @DisplayName("agregarOActualizar() → lanza CantidadExcedidaException cuando la suma de cantidades supera el stock")
    void agregarOActualizar_deberiaLanzarExcepcion_cuandoSumaCantidadesSuperaStock() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10); // stock = 10
        Carrito carrito = generarCarritoVacioFake(cliente);

        // Ya tiene 8 en el carrito
        CarritoProducto itemExistente = generarItemFake(carrito, producto, 8);
        carrito.setProductos(new ArrayList<>(List.of(itemExistente)));

        AgregarCarrito dto = new AgregarCarrito(producto.getId(), 5); // 8 + 5 = 13 > 10

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));
        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        // When / Then
        assertThrows(CarritoExceptions.CantidadExcedidaException.class,
                () -> carritoService.agregarOActualizar(dto, cliente));

        verify(carritoRepository, never()).save(any());
    }

    // agregarOActualizar() — cliente sin carrito previo

    @Test
    @DisplayName("agregarOActualizar() → crea carrito nuevo si el cliente no tenía uno")
    void agregarOActualizar_deberiaCrearCarritoNuevo_cuandoClienteNoTeniaCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10);
        AgregarCarrito dto = new AgregarCarrito(producto.getId(), 2);

        Carrito carritoNuevo = generarCarritoVacioFake(cliente);

        when(productoRepository.findById(dto.idProducto())).thenReturn(Optional.of(producto));
        // Simula que no tiene carrito → se crea uno
        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.empty());
        when(carritoRepository.save(any(Carrito.class))).thenReturn(carritoNuevo);

        // When
        carritoService.agregarOActualizar(dto, cliente);

        // Then: save debe llamarse dos veces — una para crear el carrito, otra para guardar el item
        verify(carritoRepository, times(2)).save(any(Carrito.class));
    }

    // obtenerCarritoPorCliente()

    @Test
    @DisplayName("obtenerCarritoPorCliente() → retorna DTO cuando el carrito existe")
    void obtenerCarritoPorCliente_deberiaRetornarDTO_cuandoCarritoExiste() {
        // Given
        Cliente cliente = generarClienteFake();
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoDTO.CarritoResponseDTO dtoEsperado = mock(CarritoDTO.CarritoResponseDTO.class);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        try (MockedStatic<CarritoMappers> mockedStatic = mockStatic(CarritoMappers.class)) {
            mockedStatic.when(() -> CarritoMappers.toCarritoResponseDTO(carrito)).thenReturn(dtoEsperado);

            // When
            CarritoDTO.CarritoResponseDTO resultado = carritoService.obtenerCarritoPorCliente(cliente);

            // Then
            assertEquals(dtoEsperado, resultado);
            verify(carritoRepository).findByClienteConProductos(cliente);
        }
    }

    @Test
    @DisplayName("obtenerCarritoPorCliente() → lanza CarritoNoEncontrado cuando el cliente no tiene carrito")
    void obtenerCarritoPorCliente_deberiaLanzarExcepcion_cuandoNoExisteCarrito() {
        // Given
        Cliente cliente = generarClienteFake();

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(CarritoExceptions.CarritoNoEncontrado.class,
                () -> carritoService.obtenerCarritoPorCliente(cliente));
    }

    // obtenerCarritoPorId()

    @Test
    @DisplayName("obtenerCarritoPorId() → retorna DTO cuando existe carrito para ese ID de cliente")
    void obtenerCarritoPorId_deberiaRetornarDTO_cuandoExisteCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoDTO.CarritoResponseDTO dtoEsperado = mock(CarritoDTO.CarritoResponseDTO.class);

        when(carritoRepository.findByClienteId(cliente.getId())).thenReturn(Optional.of(carrito));

        try (MockedStatic<CarritoMappers> mockedStatic = mockStatic(CarritoMappers.class)) {
            mockedStatic.when(() -> CarritoMappers.toCarritoResponseDTO(carrito)).thenReturn(dtoEsperado);

            // When
            CarritoDTO.CarritoResponseDTO resultado = carritoService.obtenerCarritoPorId(cliente.getId());

            // Then
            assertEquals(dtoEsperado, resultado);
            verify(carritoRepository).findByClienteId(cliente.getId());
        }
    }

    @Test
    @DisplayName("obtenerCarritoPorId() → lanza CarritoNoEncontrado cuando no existe carrito para ese ID")
    void obtenerCarritoPorId_deberiaLanzarExcepcion_cuandoNoExisteCarrito() {
        // Given
        Long idInexistente = 999L;

        when(carritoRepository.findByClienteId(idInexistente)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(CarritoExceptions.CarritoNoEncontrado.class,
                () -> carritoService.obtenerCarritoPorId(idInexistente));
    }

    // actualizarCantidad()

    @Test
    @DisplayName("actualizarCantidad() → lanza CarritoDeClienteNoEncontrado cuando el cliente no tiene carrito")
    void actualizarCantidad_deberiaLanzarExcepcion_cuandoClienteNoTieneCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        ActualizarCantidad dto = new ActualizarCantidad(1L, 3);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(CarritoExceptions.CarritoDeClienteNoEncontrado.class,
                () -> carritoService.actualizarCantidad(dto, cliente));

        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizarCantidad() → lanza ProductoNoEnCarritoException cuando el producto no está en el carrito")
    void actualizarCantidad_deberiaLanzarExcepcion_cuandoProductoNoEstaEnCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Carrito carrito = generarCarritoVacioFake(cliente); // carrito sin productos
        ActualizarCantidad dto = new ActualizarCantidad(999L, 3);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        // When / Then
        assertThrows(CarritoExceptions.ProductoNoEnCarritoException.class,
                () -> carritoService.actualizarCantidad(dto, cliente));

        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizarCantidad() → REEMPLAZA la cantidad (no suma) cuando el producto está en el carrito")
    void actualizarCantidad_deberiaReemplazarCantidad_cuandoProductoEstaEnCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(20);
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoProducto item = generarItemFake(carrito, producto, 5);
        carrito.setProductos(new ArrayList<>(List.of(item)));

        ActualizarCantidad dto = new ActualizarCantidad(producto.getId(), 2);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(carrito)).thenReturn(carrito);

        // When
        carritoService.actualizarCantidad(dto, cliente);

        // Then: 2, no 7
        assertEquals(2, item.getCantidad());
        verify(carritoRepository).save(carrito);
    }

    @Test
    @DisplayName("actualizarCantidad() → permite una cantidad igual al stock (caso borde)")
    void actualizarCantidad_deberiaPermitirCantidad_cuandoEsIgualAlStock() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10); // stock = 10
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoProducto item = generarItemFake(carrito, producto, 1);
        carrito.setProductos(new ArrayList<>(List.of(item)));

        ActualizarCantidad dto = new ActualizarCantidad(producto.getId(), 10);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(carrito)).thenReturn(carrito);

        // When
        carritoService.actualizarCantidad(dto, cliente);

        // Then
        assertEquals(10, item.getCantidad());
    }

    @Test
    @DisplayName("actualizarCantidad() → lanza CantidadExcedidaException y no modifica el item cuando supera el stock")
    void actualizarCantidad_deberiaLanzarExcepcion_cuandoCantidadSuperaStock() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10); // stock = 10
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoProducto item = generarItemFake(carrito, producto, 4);
        carrito.setProductos(new ArrayList<>(List.of(item)));

        ActualizarCantidad dto = new ActualizarCantidad(producto.getId(), 11);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        // When / Then
        assertThrows(CarritoExceptions.CantidadExcedidaException.class,
                () -> carritoService.actualizarCantidad(dto, cliente));

        assertEquals(4, item.getCantidad()); // sin cambios
        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizarCantidad() → elimina la línea del carrito cuando la cantidad es 0")
    void actualizarCantidad_deberiaEliminarLinea_cuandoCantidadEsCero() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(10);
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoProducto item = generarItemFake(carrito, producto, 3);
        carrito.setProductos(new ArrayList<>(List.of(item)));

        ActualizarCantidad dto = new ActualizarCantidad(producto.getId(), 0);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(carrito)).thenReturn(carrito);

        // When
        carritoService.actualizarCantidad(dto, cliente);

        // Then
        assertTrue(carrito.getProductos().isEmpty());
        verify(carritoRepository).save(carrito);
    }

// eliminarProducto()

    @Test
    @DisplayName("eliminarProducto() → lanza CarritoDeClienteNoEncontrado cuando el cliente no tiene carrito")
    void eliminarProducto_deberiaLanzarExcepcion_cuandoClienteNoTieneCarrito() {
        // Given
        Cliente cliente = generarClienteFake();

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(CarritoExceptions.CarritoDeClienteNoEncontrado.class,
                () -> carritoService.eliminarProducto(1L, cliente));
    }

    @Test
    @DisplayName("eliminarProducto() → lanza ProductoNoEnCarritoException cuando el producto no está en el carrito")
    void eliminarProducto_deberiaLanzarExcepcion_cuandoProductoNoEstaEnCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Carrito carrito = generarCarritoVacioFake(cliente);

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        // When / Then
        assertThrows(CarritoExceptions.ProductoNoEnCarritoException.class,
                () -> carritoService.eliminarProducto(999L, cliente));
    }

    @Test
    @DisplayName("eliminarProducto() → elimina la línea completa sin importar la cantidad que tuviera")
    void eliminarProducto_deberiaEliminarLinea_cuandoProductoEstaEnCarrito() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto producto = generarProductoFake(50);
        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoProducto item = generarItemFake(carrito, producto, 25);
        carrito.setProductos(new ArrayList<>(List.of(item)));

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        // When
        carritoService.eliminarProducto(producto.getId(), cliente);

        // Then
        assertTrue(carrito.getProductos().isEmpty());
    }

    @Test
    @DisplayName("eliminarProducto() → elimina solo el producto indicado y conserva los demás")
    void eliminarProducto_deberiaConservarOtrosProductos_cuandoElimina() {
        // Given
        Cliente cliente = generarClienteFake();
        Producto productoA = generarProductoFake(10);
        Producto productoB = generarProductoFake(10);
        productoA.setId(1L);
        productoB.setId(2L); // ids fijos para evitar colisiones aleatorias

        Carrito carrito = generarCarritoVacioFake(cliente);
        CarritoProducto itemA = generarItemFake(carrito, productoA, 2);
        CarritoProducto itemB = generarItemFake(carrito, productoB, 4);
        carrito.setProductos(new ArrayList<>(List.of(itemA, itemB)));

        when(carritoRepository.findByClienteConProductos(cliente)).thenReturn(Optional.of(carrito));

        // When
        carritoService.eliminarProducto(1L, cliente);

        // Then
        assertEquals(1, carrito.getProductos().size());
        assertSame(itemB, carrito.getProductos().get(0));
    }
}
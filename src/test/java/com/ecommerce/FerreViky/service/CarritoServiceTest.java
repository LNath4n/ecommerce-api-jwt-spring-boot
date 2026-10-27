package com.ecommerce.FerreViky.service;

import static com.ecommerce.FerreViky.utils.Fakes.*;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.*;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.service.StorageService.contract.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CarritoService - Pruebas unitarias")
class CarritoServiceTest {
    @Mock private ProductoRepository productoRepository;
    @Mock private CarritoRepository carritoRepository;
    @Mock private CodigoVirtualRepository codigoVirtualRepository;
    @Mock private StorageService storageService;

    @InjectMocks private CarritoService carritoService;

    @Test
    @DisplayName("Al agregar un producto que no existe este lanza ProductoNoEncontradoException")
    void agregarOActualizar_productoNoExiste_lanzaProductoNoEncontrado() {
        //Arrange
        Cliente c = generarClienteFake();
        AgregarCarrito dto = new AgregarCarrito(9L,9);

        //Act
        when(productoRepository.findById(9L)).thenReturn(Optional.empty());

        //Assert
        assertThrows(ProductoNoEncontradoException.class,()->carritoService.agregarOActualizar(dto,c));
    }

    @Test
    @DisplayName("Al agregar un producto inactivo este lanza ProductoNoEncontradoException")
    void agregarOActualizar_productoInactivo_lanzaProductoNoEncontrado() {
        //Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(producto -> {producto.setActivo(false);});
        AgregarCarrito dto = new AgregarCarrito(p.getId(),9);

        //Act
        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        //Assert
        assertThrows(ProductoNoEncontradoException.class,()->carritoService.agregarOActualizar(dto,c));
    }

    @Test
    @DisplayName("Al agregar un producto con cantidad cero o negativa lanza CantidadNoValida")
    void agregarOActualizar_cantidadNoValida_lanzaCantidadNoValida() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        AgregarCarrito dtoCero = agregarDto(p.getId(), 0);
        AgregarCarrito dtoNegativo = agregarDto(p.getId(), -7);

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act + Assert
        assertThrows(CantidadNoValida.class, () -> carritoService.agregarOActualizar(dtoCero, c));
        assertThrows(CantidadNoValida.class, () -> carritoService.agregarOActualizar(dtoNegativo, c));
        verifyNoInteractions(carritoRepository);
    }
    @Test
    @DisplayName("Al agregar un producto a un cliente sin carrito se crea el carrito y se guarda")
    void agregarOActualizar_clienteSinCarrito_creaCarritoNuevo() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        AgregarCarrito dto = agregarDto(p.getId(), 2);

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.empty());
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(5L);

        // Act
        carritoService.agregarOActualizar(dto, c);

        // Assert
        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepository, times(2)).save(captor.capture());

        Carrito guardado = captor.getValue();
        assertEquals(c, guardado.getCliente());
        assertNotNull(guardado.getFechaCreacion());
        assertEquals(1, guardado.getProductos().size());
        assertEquals(p, guardado.getProductos().get(0).getProducto());
        assertEquals(2, guardado.getProductos().get(0).getCantidad());
    }
    @Test
    @DisplayName("Al agregar un producto nuevo al carrito se agrega como linea con la cantidad pedida")
    void agregarOActualizar_productoNuevo_agregaLineaNueva() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Producto nuevo = generarProductoFake(p -> p.setId(2L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, existente, 1);            // el carrito ya tiene OTRO producto
        AgregarCarrito dto = agregarDto(2L, 3);

        when(productoRepository.findById(2L)).thenReturn(Optional.of(nuevo));
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(2L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        carritoService.agregarOActualizar(dto, c);

        // Assert
        assertEquals(2, carrito.getProductos().size());
        ProductoEnCarrito lineaNueva = carrito.getProductos().get(1);
        assertEquals(nuevo, lineaNueva.getProducto());
        assertEquals(3, lineaNueva.getCantidad());
        assertEquals(carrito, lineaNueva.getCarrito());
        verify(carritoRepository).save(carrito);
    }

    @Test
    @DisplayName("Al agregar un producto que ya esta en el carrito se suma la cantidad a la linea existente")
    void agregarOActualizar_productoExistente_sumaCantidad() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        int cantidadExistente = 1;
        ProductoEnCarrito linea = agregarItem(carrito, existente, cantidadExistente);            // el carrito ya tiene el producto
        int cantidadDeseadaParaAgregar = 3;
        AgregarCarrito dto = agregarDto(existente.getId(), cantidadDeseadaParaAgregar);
        int cantidadDeseadaFinal = cantidadExistente + cantidadDeseadaParaAgregar;

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(existente.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        carritoService.agregarOActualizar(dto, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertEquals(cantidadDeseadaFinal, linea.getCantidad());   // la MISMA linea fue la que cambio
        verify(carritoRepository).save(carrito);
    }

    @Test
    @DisplayName("Al pedir exactamente el stock disponible se permite agregar")
    void agregarOActualizar_cantidadIgualAlStock_agrega() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        int cantidadExistente = 1;
        agregarItem(carrito, existente, cantidadExistente);            // el carrito ya tiene el producto
        int cantidadDeseadaParaAgregar = 3;
        AgregarCarrito dto = agregarDto(existente.getId(), cantidadDeseadaParaAgregar);
        int cantidadDeseadaFinal = cantidadExistente + cantidadDeseadaParaAgregar;

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(existente.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn((long) cantidadDeseadaFinal);

        // Act
        carritoService.agregarOActualizar(dto, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertEquals(cantidadDeseadaFinal, carrito.getProductos().get(0).getCantidad());
        verify(carritoRepository).save(carrito);
    }

    @Test
    @DisplayName("Al agregar mas cantidad que el stock disponible lanza CantidadExcedidaException")
    void agregarOActualizar_superaStock_lanzaCantidadExcedida() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));   // carrito vacio: solo falla por lo pedido
        int cantidadDeseada = 3;
        AgregarCarrito dto = agregarDto(existente.getId(), cantidadDeseada);

        when(productoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(existente.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(2L);

        // Act + Assert
        assertThrows(CantidadExcedidaException.class, () -> carritoService.agregarOActualizar(dto, c));
        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Al sumar a una linea existente el stock se valida contra la cantidad total y no contra la pedida")
    void agregarOActualizar_sumaSuperaStock_lanzaCantidadExcedida() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, p, 3);                       // ya tiene 3 en el carrito
        AgregarCarrito dto = agregarDto(p.getId(), 3);    // pide 3 mas entonccesss total 6

        when(productoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(5L);                          // stock 5

        // Act + Assert
        assertThrows(CantidadExcedidaException.class,
                () -> carritoService.agregarOActualizar(dto, c));
        verify(carritoRepository, never()).save(any());
    }

    //2
    //  actualizarCantidad
    @Test
    @DisplayName("Al actualizar la cantidad de un cliente sin carrito lanza CarritoDeClienteNoEncontrado")
    void actualizarCantidad_sinCarrito_lanzaCarritoDeClienteNoEncontrado() {
        // Arrange
        Cliente c = generarClienteFake();
        ActualizarCantidad dto = actualizarDto(1L, 2);
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> carritoService.actualizarCantidad(dto, c));
        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Al actualizar la cantidad de un producto que no esta en el carrito lanza ProductoNoEnCarritoException")
    void actualizarCantidad_productoNoEnCarrito_lanzaProductoNoEnCarrito() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto enCarrito = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, enCarrito, 2);
        ActualizarCantidad dto = actualizarDto(99L, 3);   // el 99 no esta en el carrito

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));

        // Act + Assert
        assertThrows(ProductoNoEnCarritoException.class,
                () -> carritoService.actualizarCantidad(dto, c));
        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Al actualizar la cantidad esta reemplaza a la anterior y no se suma")
    void actualizarCantidad_cantidadValida_reemplazaCantidad() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito linea = agregarItem(carrito, p, 2);   // tiene 2
        ActualizarCantidad dto = actualizarDto(1L, 5);          // ahora quiere 5 (si sumara seria 7)

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(1L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        carritoService.actualizarCantidad(dto, c);

        // Assert
        assertEquals(5, linea.getCantidad());
        assertEquals(1, carrito.getProductos().size());
        verify(carritoRepository).save(carrito);
    }

    @Test
    @DisplayName("Al actualizar la cantidad a cero la linea se elimina del carrito")
    void actualizarCantidad_cantidadCero_eliminaLinea() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto aBorrar = generarProductoFake(p -> p.setId(1L));
        Producto otro = generarProductoFake(p -> p.setId(2L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito lineaBorrada = agregarItem(carrito, aBorrar, 2);
        ProductoEnCarrito lineaOtra = agregarItem(carrito, otro, 1);
        ActualizarCantidad dto = actualizarDto(1L, 0);

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));

        // Act
        carritoService.actualizarCantidad(dto, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertFalse(carrito.getProductos().contains(lineaBorrada));
        assertTrue(carrito.getProductos().contains(lineaOtra));
        verifyNoInteractions(codigoVirtualRepository);   // con cantidad 0 ni consulta el stock
        verify(carritoRepository).save(carrito);
    }

    // 3
    
    @Test
    @DisplayName("Al actualizar la cantidad por encima del stock lanza CantidadExcedidaException y conserva la anterior")
    void actualizarCantidad_superaStock_lanzaCantidadExcedida() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito linea = agregarItem(carrito, p, 2);
        ActualizarCantidad dto = actualizarDto(1L, 8);

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(1L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(5L);

        // Act + Assert
        assertThrows(CantidadExcedidaException.class,
                () -> carritoService.actualizarCantidad(dto, c));
        assertEquals(2, linea.getCantidad());   // sigue con la anterior
        verify(carritoRepository, never()).save(any());
    }

    //  eliminarProducto 
    @Test
    @DisplayName("Al eliminar un producto de un cliente sin carrito lanza CarritoDeClienteNoEncontrado")
    void eliminarProducto_sinCarrito_lanzaCarritoDeClienteNoEncontrado() {
        // Arrange
        Cliente c = generarClienteFake();
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> carritoService.eliminarProducto(1L, c));
    }

    @Test
    @DisplayName("Al eliminar un producto que no esta en el carrito lanza ProductoNoEnCarritoException")
    void eliminarProducto_productoNoEnCarrito_lanzaProductoNoEnCarrito() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto enCarrito = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, enCarrito, 2);

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));

        // Act + Assert
        assertThrows(ProductoNoEnCarritoException.class,
                () -> carritoService.eliminarProducto(99L, c));
        assertEquals(1, carrito.getProductos().size());   // no toco nada
    }

    @Test
    @DisplayName("Al eliminar un producto este desaparece del carrito y las demas lineas se conservan")
    void eliminarProducto_existe_quitaSoloEsaLinea() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto aBorrar = generarProductoFake(p -> p.setId(1L));
        Producto otro = generarProductoFake(p -> p.setId(2L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito lineaBorrada = agregarItem(carrito, aBorrar, 2);
        ProductoEnCarrito lineaOtra = agregarItem(carrito, otro, 1);

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));

        // Act
        carritoService.eliminarProducto(1L, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertFalse(carrito.getProductos().contains(lineaBorrada));
        assertTrue(carrito.getProductos().contains(lineaOtra));
        verify(carritoRepository, never()).save(any());   // confia en orphanRemoval
    }

    //  obtenerCarritoPorCliente 
    @Test
    @DisplayName("Al obtener el carrito de un cliente sin carrito lanza CarritoNoEncontrado")
    void obtenerCarritoPorCliente_noExiste_lanzaCarritoNoEncontrado() {
        // Arrange
        Cliente c = generarClienteFake();
        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoNoEncontrado.class,
                () -> carritoService.obtenerCarritoPorCliente(c));
    }


    //  obtenerCarritoPorId 
    @Test
    @DisplayName("Al obtener el carrito por id de cliente que no tiene carrito lanza CarritoNoEncontrado")
    void obtenerCarritoPorId_noExiste_lanzaCarritoNoEncontrado() {
        // Arrange
        when(carritoRepository.findByClienteId(5L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoNoEncontrado.class,
                () -> carritoService.obtenerCarritoPorId(5L));
    }


    @Test
    @DisplayName("Al actualizar la cantidad a exactamente el stock disponible se permite")
    void actualizarCantidad_cantidadIgualAlStock_permite() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito linea = agregarItem(carrito, p, 1);
        ActualizarCantidad dto = actualizarDto(1L, 10);

        when(carritoRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));
        when(codigoVirtualRepository.countByProductoIdAndEstado(1L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        carritoService.actualizarCantidad(dto, c);

        // Assert
        assertEquals(10, linea.getCantidad());
        verify(carritoRepository).save(carrito);
    }
}
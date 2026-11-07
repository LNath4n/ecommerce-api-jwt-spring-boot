package com.ecommerce.FerreViky.service;

import static com.ecommerce.FerreViky.utils.Fakes.*;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.*;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService - Unit tests")
class CartServiceTest {
    @Mock private ProductoRepository productRepository;
    @Mock private CarritoRepository cartRepository;
    @Mock private CodigoVirtualRepository virtualCodeRepository;
    @Mock private StorageService storageService;

    @InjectMocks private CartService cartService;

    //  addOrUpdateCartItem
    @Test
    @DisplayName("When adding a product that does not exist, it throws ProductoNoEncontradoException")
    void addOrUpdateCartItem_productNotFound_throwsProductNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        AgregarCarrito dto = new AgregarCarrito(9L,9);

        // Act
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        // Assert
        assertThrows(ProductoNoEncontradoException.class,()-> cartService.addOrUpdateCartItem(dto,c));
    }

    @Test
    @DisplayName("When adding an inactive product, it throws ProductoNoEncontradoException")
    void addOrUpdateCartItem_inactiveProduct_throwsProductNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(producto -> {producto.setActivo(false);});
        AgregarCarrito dto = new AgregarCarrito(p.getId(),9);

        // Act
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Assert
        assertThrows(ProductoNoEncontradoException.class,()-> cartService.addOrUpdateCartItem(dto,c));
    }

    @Test
    @DisplayName("When adding a product with a zero or negative quantity, it throws CantidadNoValida")
    void addOrUpdateCartItem_invalidQuantity_throwsInvalidQuantity() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        AgregarCarrito dtoCero = agregarDto(p.getId(), 0);
        AgregarCarrito dtoNegativo = agregarDto(p.getId(), -7);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Act + Assert
        assertThrows(CantidadNoValida.class, () -> cartService.addOrUpdateCartItem(dtoCero, c));
        assertThrows(CantidadNoValida.class, () -> cartService.addOrUpdateCartItem(dtoNegativo, c));
        verifyNoInteractions(cartRepository);
    }
    @Test
    @DisplayName("When adding a product for a customer without a cart, the cart is created and saved")
    void addOrUpdateCartItem_customerWithoutCart_createsNewCart() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        AgregarCarrito dto = agregarDto(p.getId(), 2);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(virtualCodeRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(5L);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(cartRepository, times(2)).saveAndFlush(captor.capture());

        Carrito guardado = captor.getValue();
        assertEquals(c, guardado.getCliente());
        assertNotNull(guardado.getFechaCreacion());
        assertEquals(1, guardado.getProductos().size());
        assertEquals(p, guardado.getProductos().get(0).getProducto());
        assertEquals(2, guardado.getProductos().get(0).getCantidad());
    }
    @Test
    @DisplayName("When adding a new product to the cart, it is added as a line with the requested quantity")
    void addOrUpdateCartItem_newProduct_addsNewLine() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Producto nuevo = generarProductoFake(p -> p.setId(2L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, existente, 1);            // the cart already has ANOTHER product
        AgregarCarrito dto = agregarDto(2L, 3);

        when(productRepository.findById(2L)).thenReturn(Optional.of(nuevo));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(2L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        assertEquals(2, carrito.getProductos().size());
        ProductoEnCarrito lineaNueva = carrito.getProductos().get(1);
        assertEquals(nuevo, lineaNueva.getProducto());
        assertEquals(3, lineaNueva.getCantidad());
        assertEquals(carrito, lineaNueva.getCarrito());
        verify(cartRepository).saveAndFlush(carrito);
    }

    @Test
    @DisplayName("When adding a product that is already in the cart, the quantity is added to the existing line")
    void addOrUpdateCartItem_existingProduct_addsQuantity() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        int cantidadExistente = 1;
        ProductoEnCarrito linea = agregarItem(carrito, existente, cantidadExistente);            // the cart already has the product
        int cantidadDeseadaParaAgregar = 3;
        AgregarCarrito dto = agregarDto(existente.getId(), cantidadDeseadaParaAgregar);
        int cantidadDeseadaFinal = cantidadExistente + cantidadDeseadaParaAgregar;

        when(productRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(existente.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertEquals(cantidadDeseadaFinal, linea.getCantidad());   // the SAME line was the one that changed
        verify(cartRepository).saveAndFlush(carrito);
    }

    @Test
    @DisplayName("When requesting exactly the available stock, adding is allowed")
    void addOrUpdateCartItem_quantityEqualToStock_adds() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        int cantidadExistente = 1;
        agregarItem(carrito, existente, cantidadExistente);            // the cart already has the product
        int cantidadDeseadaParaAgregar = 3;
        AgregarCarrito dto = agregarDto(existente.getId(), cantidadDeseadaParaAgregar);
        int cantidadDeseadaFinal = cantidadExistente + cantidadDeseadaParaAgregar;

        when(productRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(existente.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn((long) cantidadDeseadaFinal);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertEquals(cantidadDeseadaFinal, carrito.getProductos().get(0).getCantidad());
        verify(cartRepository).saveAndFlush(carrito);
    }

    @Test
    @DisplayName("When adding more than the available stock, it throws CantidadExcedidaException")
    void addOrUpdateCartItem_exceedsStock_throwsQuantityExceeded() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto existente = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));   // empty cart: it only fails because of the requested amount
        int cantidadDeseada = 3;
        AgregarCarrito dto = agregarDto(existente.getId(), cantidadDeseada);

        when(productRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(existente.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(2L);

        // Act + Assert
        assertThrows(CantidadExcedidaException.class, () -> cartService.addOrUpdateCartItem(dto, c));
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When adding to an existing line, stock is validated against the total quantity and not the requested one")
    void addOrUpdateCartItem_sumExceedsStock_throwsQuantityExceeded() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, p, 3);                       // already has 3 in the cart
        AgregarCarrito dto = agregarDto(p.getId(), 3);    // requests 3 more, so the total is 6

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(5L);                          // stock is 5

        // Act + Assert
        assertThrows(CantidadExcedidaException.class,
                () -> cartService.addOrUpdateCartItem(dto, c));
        verify(cartRepository, never()).saveAndFlush(any());
    }

    //  updateCartItemQuantity
    @Test
    @DisplayName("When updating the quantity for a customer without a cart, it throws CarritoDeClienteNoEncontrado")
    void updateCartItemQuantity_noCart_throwsCustomerCartNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        ActualizarCantidad dto = actualizarDto(1L, 2);
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> cartService.updateCartItemQuantity(dto, c));
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When updating the quantity of a product that is not in the cart, it throws ProductoNoEnCarritoException")
    void updateCartItemQuantity_productNotInCart_throwsProductNotInCart() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto enCarrito = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, enCarrito, 2);
        ActualizarCantidad dto = actualizarDto(99L, 3);   // product 99 is not in the cart

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));

        // Act + Assert
        assertThrows(ProductoNoEnCarritoException.class,
                () -> cartService.updateCartItemQuantity(dto, c));
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When updating the quantity, it replaces the previous one instead of adding to it")
    void updateCartItemQuantity_validQuantity_replacesQuantity() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito linea = agregarItem(carrito, p, 2);   // has 2
        ActualizarCantidad dto = actualizarDto(1L, 5);          // now wants 5 (adding would give 7)

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(1L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        cartService.updateCartItemQuantity(dto, c);

        // Assert
        assertEquals(5, linea.getCantidad());
        assertEquals(1, carrito.getProductos().size());
        verify(cartRepository).save(carrito);
    }

    @Test
    @DisplayName("When updating the quantity to zero, the line is removed from the cart")
    void updateCartItemQuantity_zeroQuantity_removesLine() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto aBorrar = generarProductoFake(p -> p.setId(1L));
        Producto otro = generarProductoFake(p -> p.setId(2L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito lineaBorrada = agregarItem(carrito, aBorrar, 2);
        ProductoEnCarrito lineaOtra = agregarItem(carrito, otro, 1);
        ActualizarCantidad dto = actualizarDto(1L, 0);

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));

        // Act
        cartService.updateCartItemQuantity(dto, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertFalse(carrito.getProductos().contains(lineaBorrada));
        assertTrue(carrito.getProductos().contains(lineaOtra));
        verifyNoInteractions(virtualCodeRepository);   // with quantity 0 it does not even check stock
        verify(cartRepository).save(carrito);
    }

    @Test
    @DisplayName("When updating the quantity above the stock, it throws CantidadExcedidaException and keeps the previous quantity")
    void updateCartItemQuantity_exceedsStock_throwsQuantityExceeded() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito linea = agregarItem(carrito, p, 2);
        ActualizarCantidad dto = actualizarDto(1L, 8);

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(1L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(5L);

        // Act + Assert
        assertThrows(CantidadExcedidaException.class,
                () -> cartService.updateCartItemQuantity(dto, c));
        assertEquals(2, linea.getCantidad());   // still has the previous quantity
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When updating the quantity to exactly the available stock, it is allowed")
    void updateCartItemQuantity_quantityEqualToStock_allows() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito linea = agregarItem(carrito, p, 1);
        ActualizarCantidad dto = actualizarDto(1L, 10);

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(1L, CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);

        // Act
        cartService.updateCartItemQuantity(dto, c);

        // Assert
        assertEquals(10, linea.getCantidad());
        verify(cartRepository).save(carrito);
    }

    //  removeProductFromCart
    @Test
    @DisplayName("When removing a product for a customer without a cart, it throws CarritoDeClienteNoEncontrado")
    void removeProductFromCart_noCart_throwsCustomerCartNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> cartService.removeProductFromCart(1L, c));
    }

    @Test
    @DisplayName("When removing a product that is not in the cart, it throws ProductoNoEnCarritoException")
    void removeProductFromCart_productNotInCart_throwsProductNotInCart() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto enCarrito = generarProductoFake(p -> p.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, enCarrito, 2);

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));

        // Act + Assert
        assertThrows(ProductoNoEnCarritoException.class,
                () -> cartService.removeProductFromCart(99L, c));
        assertEquals(1, carrito.getProductos().size());   // nothing was touched
    }

    @Test
    @DisplayName("When removing a product, it disappears from the cart and the other lines are kept")
    void removeProductFromCart_exists_removesOnlyThatLine() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto aBorrar = generarProductoFake(p -> p.setId(1L));
        Producto otro = generarProductoFake(p -> p.setId(2L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        ProductoEnCarrito lineaBorrada = agregarItem(carrito, aBorrar, 2);
        ProductoEnCarrito lineaOtra = agregarItem(carrito, otro, 1);

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));

        // Act
        cartService.removeProductFromCart(1L, c);

        // Assert
        assertEquals(1, carrito.getProductos().size());
        assertFalse(carrito.getProductos().contains(lineaBorrada));
        assertTrue(carrito.getProductos().contains(lineaOtra));
        verify(cartRepository, never()).save(any());   // relies on orphanRemoval
    }

    //  getCartByCustomer
    @Test
    @DisplayName("When getting the cart of a customer without a cart, it throws CarritoNoEncontrado")
    void getCartByCustomer_notFound_throwsCartNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        when(cartRepository.findByClienteConProductos(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoNoEncontrado.class,
                () -> cartService.getCartByCustomer(c));
    }


    //  getCartByCustomerId
    @Test
    @DisplayName("When getting the cart by the id of a customer without a cart, it throws CarritoNoEncontrado")
    void getCartByCustomerId_notFound_throwsCartNotFound() {
        // Arrange
        when(cartRepository.findByClienteId(5L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoNoEncontrado.class,
                () -> cartService.getCartByCustomerId(5L));
    }


    //  NEW TESTS 

    //  addOrUpdateCartItem (new)
    @Test
    @DisplayName("When creating the cart and the database rejects it for integrity, it throws ConflictoConcurrenteException")
    void addOrUpdateCartItem_dataIntegrityWhenCreatingCart_throwsConcurrentConflict() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        AgregarCarrito dto = agregarDto(p.getId(), 2);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Carrito.class)))
                .thenThrow(new DataIntegrityViolationException("unique cart per customer"));

        // Act + Assert
        assertThrows(ConflictoConcurrenteException.class,
                () -> cartService.addOrUpdateCartItem(dto, c));
    }

    @Test
    @DisplayName("When saving the cart and the database rejects it for integrity, it throws ConflictoConcurrenteException")
    void addOrUpdateCartItem_dataIntegrityWhenSaving_throwsConcurrentConflict() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        AgregarCarrito dto = agregarDto(p.getId(), 1);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));
        when(virtualCodeRepository.countByProductoIdAndEstado(p.getId(), CodigoVirtual.EstadoCodigo.DISPONIBLE))
                .thenReturn(10L);
        when(cartRepository.saveAndFlush(carrito))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        // Act + Assert
        assertThrows(ConflictoConcurrenteException.class,
                () -> cartService.addOrUpdateCartItem(dto, c));
    }

    @Test
    @DisplayName("When the resulting quantity overflows an int, it throws ArithmeticException before validating stock")
    void addOrUpdateCartItem_quantityOverflow_throwsArithmeticException() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake();
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, p, Integer.MAX_VALUE);
        AgregarCarrito dto = agregarDto(p.getId(), 1);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));

        // Act + Assert
        assertThrows(ArithmeticException.class,
                () -> cartService.addOrUpdateCartItem(dto, c));
        verify(cartRepository, never()).saveAndFlush(any());
    }

    //  clearCart
    @Test
    @DisplayName("When clearing the cart, all lines are removed but the cart is kept")
    void clearCart_exists_removesAllLines() {
        // Arrange
        Cliente c = generarClienteFake();
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, generarProductoFake(p -> p.setId(1L)), 2);
        agregarItem(carrito, generarProductoFake(p -> p.setId(2L)), 1);

        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.of(carrito));

        // Act
        cartService.clearCart(c);

        // Assert
        assertTrue(carrito.getProductos().isEmpty());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    @DisplayName("When clearing the cart of a customer without a cart, it throws CarritoDeClienteNoEncontrado")
    void clearCart_noCart_throwsCustomerCartNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        when(cartRepository.findByClienteParaActualizar(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> cartService.clearCart(c));
    }

    //  getCartByCustomer / getCartByCustomerId (success)
    @Test
    @DisplayName("When getting an existing cart, it returns the DTO and resolves each product's image URL")
    void getCartByCustomer_exists_returnsDtoAndResolvesImageUrls() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, p, 2);

        when(cartRepository.findByClienteConProductos(c)).thenReturn(Optional.of(carrito));

        // Act
        CarritoResponseDTO resultado = cartService.getCartByCustomer(c);

        // Assert
        assertNotNull(resultado);
        verify(storageService).getPublicUrl(p.getImagenKey());
    }

    @Test
    @DisplayName("When getting a cart by customer id, it returns the DTO and resolves each product's image URL")
    void getCartByCustomerId_exists_returnsDtoAndResolvesImageUrls() {
        // Arrange
        Cliente c = generarClienteFake();
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        Carrito carrito = generarCarritoFake(car -> car.setCliente(c));
        agregarItem(carrito, p, 2);

        when(cartRepository.findByClienteId(c.getId())).thenReturn(Optional.of(carrito));

        // Act
        CarritoResponseDTO resultado = cartService.getCartByCustomerId(c.getId());

        // Assert
        assertNotNull(resultado);
        verify(storageService).getPublicUrl(p.getImagenKey());
    }
}
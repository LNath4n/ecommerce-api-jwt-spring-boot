package com.ecommerce.FerreViky.service;

import static com.ecommerce.FerreViky.utils.Fakes.*;

import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.UpdateCartItemQuantityRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.AddCartItemRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartResponse;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.*;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.*;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
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
    @Mock private ProductRepository productRepository;
    @Mock private CartRepository cartRepository;
    @Mock private VirtualCodeRepository virtualCodeRepository;
    @Mock private StorageService storageService;

    @InjectMocks private CartService cartService;

    //  addOrUpdateCartItem
    @Test
    @DisplayName("When adding a product that does not exist, it throws ProductoNoEncontradoException")
    void addOrUpdateCartItem_productNotFound_throwsProductNotFound() {
        // Arrange
        Customer c = generarClienteFake();
        AddCartItemRequest dto = new AddCartItemRequest(9L,9);

        // Act
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        // Assert
        assertThrows(ProductoNoEncontradoException.class,()-> cartService.addOrUpdateCartItem(dto,c));
    }

    @Test
    @DisplayName("When adding an inactive product, it throws ProductoNoEncontradoException")
    void addOrUpdateCartItem_inactiveProduct_throwsProductNotFound() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake(producto -> {producto.setActive(false);});
        AddCartItemRequest dto = new AddCartItemRequest(p.getId(),9);

        // Act
        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));

        // Assert
        assertThrows(ProductoNoEncontradoException.class,()-> cartService.addOrUpdateCartItem(dto,c));
    }

    @Test
    @DisplayName("When adding a product with a zero or negative quantity, it throws CantidadNoValida")
    void addOrUpdateCartItem_invalidQuantity_throwsInvalidQuantity() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake();
        AddCartItemRequest dtoCero = agregarDto(p.getId(), 0);
        AddCartItemRequest dtoNegativo = agregarDto(p.getId(), -7);

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
        Customer c = generarClienteFake();
        Product p = generarProductoFake();
        AddCartItemRequest dto = agregarDto(p.getId(), 2);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(virtualCodeRepository.countByProductIdAndStatus(p.getId(), VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(5L);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, times(2)).saveAndFlush(captor.capture());

        Cart guardado = captor.getValue();
        assertEquals(c, guardado.getCustomer());
        assertNotNull(guardado.getCreatedAt());
        assertEquals(1, guardado.getItems().size());
        assertEquals(p, guardado.getItems().get(0).getProduct());
        assertEquals(2, guardado.getItems().get(0).getQuantity());
    }
    @Test
    @DisplayName("When adding a new product to the cart, it is added as a line with the requested quantity")
    void addOrUpdateCartItem_newProduct_addsNewLine() {
        // Arrange
        Customer c = generarClienteFake();
        Product existente = generarProductoFake(p -> p.setId(1L));
        Product nuevo = generarProductoFake(p -> p.setId(2L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, existente, 1);            // the cart already has ANOTHER product
        AddCartItemRequest dto = agregarDto(2L, 3);

        when(productRepository.findById(2L)).thenReturn(Optional.of(nuevo));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(2L, VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(10L);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        assertEquals(2, cart.getItems().size());
        ItemInCart lineaNueva = cart.getItems().get(1);
        assertEquals(nuevo, lineaNueva.getProduct());
        assertEquals(3, lineaNueva.getQuantity());
        assertEquals(cart, lineaNueva.getCart());
        verify(cartRepository).saveAndFlush(cart);
    }

    @Test
    @DisplayName("When adding a product that is already in the cart, the quantity is added to the existing line")
    void addOrUpdateCartItem_existingProduct_addsQuantity() {
        // Arrange
        Customer c = generarClienteFake();
        Product existente = generarProductoFake(p -> p.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        int cantidadExistente = 1;
        ItemInCart linea = agregarItem(cart, existente, cantidadExistente);            // the cart already has the product
        int cantidadDeseadaParaAgregar = 3;
        AddCartItemRequest dto = agregarDto(existente.getId(), cantidadDeseadaParaAgregar);
        int cantidadDeseadaFinal = cantidadExistente + cantidadDeseadaParaAgregar;

        when(productRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(existente.getId(), VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(10L);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        assertEquals(1, cart.getItems().size());
        assertEquals(cantidadDeseadaFinal, linea.getQuantity());   // the SAME line was the one that changed
        verify(cartRepository).saveAndFlush(cart);
    }

    @Test
    @DisplayName("When requesting exactly the available stock, adding is allowed")
    void addOrUpdateCartItem_quantityEqualToStock_adds() {
        // Arrange
        Customer c = generarClienteFake();
        Product existente = generarProductoFake(p -> p.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        int cantidadExistente = 1;
        agregarItem(cart, existente, cantidadExistente);            // the cart already has the product
        int cantidadDeseadaParaAgregar = 3;
        AddCartItemRequest dto = agregarDto(existente.getId(), cantidadDeseadaParaAgregar);
        int cantidadDeseadaFinal = cantidadExistente + cantidadDeseadaParaAgregar;

        when(productRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(existente.getId(), VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn((long) cantidadDeseadaFinal);

        // Act
        cartService.addOrUpdateCartItem(dto, c);

        // Assert
        assertEquals(1, cart.getItems().size());
        assertEquals(cantidadDeseadaFinal, cart.getItems().get(0).getQuantity());
        verify(cartRepository).saveAndFlush(cart);
    }

    @Test
    @DisplayName("When adding more than the available stock, it throws CantidadExcedidaException")
    void addOrUpdateCartItem_exceedsStock_throwsQuantityExceeded() {
        // Arrange
        Customer c = generarClienteFake();
        Product existente = generarProductoFake(p -> p.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));   // empty cart: it only fails because of the requested amount
        int cantidadDeseada = 3;
        AddCartItemRequest dto = agregarDto(existente.getId(), cantidadDeseada);

        when(productRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(existente.getId(), VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(2L);

        // Act + Assert
        assertThrows(CantidadExcedidaException.class, () -> cartService.addOrUpdateCartItem(dto, c));
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When adding to an existing line, stock is validated against the total quantity and not the requested one")
    void addOrUpdateCartItem_sumExceedsStock_throwsQuantityExceeded() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake();
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, p, 3);                       // already has 3 in the cart
        AddCartItemRequest dto = agregarDto(p.getId(), 3);    // requests 3 more, so the total is 6

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(p.getId(), VirtualCode.CodeStatus.AVAILABLE))
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
        Customer c = generarClienteFake();
        UpdateCartItemQuantityRequest dto = actualizarDto(1L, 2);
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> cartService.updateCartItemQuantity(dto, c));
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When updating the quantity of a product that is not in the cart, it throws ProductoNoEnCarritoException")
    void updateCartItemQuantity_productNotInCart_throwsProductNotInCart() {
        // Arrange
        Customer c = generarClienteFake();
        Product enCarrito = generarProductoFake(p -> p.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, enCarrito, 2);
        UpdateCartItemQuantityRequest dto = actualizarDto(99L, 3);   // product 99 is not in the cart

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));

        // Act + Assert
        assertThrows(ProductoNoEnCarritoException.class,
                () -> cartService.updateCartItemQuantity(dto, c));
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When updating the quantity, it replaces the previous one instead of adding to it")
    void updateCartItemQuantity_validQuantity_replacesQuantity() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake(prod -> prod.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        ItemInCart linea = agregarItem(cart, p, 2);   // has 2
        UpdateCartItemQuantityRequest dto = actualizarDto(1L, 5);          // now wants 5 (adding would give 7)

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(1L, VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(10L);

        // Act
        cartService.updateCartItemQuantity(dto, c);

        // Assert
        assertEquals(5, linea.getQuantity());
        assertEquals(1, cart.getItems().size());
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("When updating the quantity to zero, the line is removed from the cart")
    void updateCartItemQuantity_zeroQuantity_removesLine() {
        // Arrange
        Customer c = generarClienteFake();
        Product aBorrar = generarProductoFake(p -> p.setId(1L));
        Product otro = generarProductoFake(p -> p.setId(2L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        ItemInCart lineaBorrada = agregarItem(cart, aBorrar, 2);
        ItemInCart lineaOtra = agregarItem(cart, otro, 1);
        UpdateCartItemQuantityRequest dto = actualizarDto(1L, 0);

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));

        // Act
        cartService.updateCartItemQuantity(dto, c);

        // Assert
        assertEquals(1, cart.getItems().size());
        assertFalse(cart.getItems().contains(lineaBorrada));
        assertTrue(cart.getItems().contains(lineaOtra));
        verifyNoInteractions(virtualCodeRepository);   // with quantity 0 it does not even check stock
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("When updating the quantity above the stock, it throws CantidadExcedidaException and keeps the previous quantity")
    void updateCartItemQuantity_exceedsStock_throwsQuantityExceeded() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake(prod -> prod.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        ItemInCart linea = agregarItem(cart, p, 2);
        UpdateCartItemQuantityRequest dto = actualizarDto(1L, 8);

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(1L, VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(5L);

        // Act + Assert
        assertThrows(CantidadExcedidaException.class,
                () -> cartService.updateCartItemQuantity(dto, c));
        assertEquals(2, linea.getQuantity());   // still has the previous quantity
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When updating the quantity to exactly the available stock, it is allowed")
    void updateCartItemQuantity_quantityEqualToStock_allows() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake(prod -> prod.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        ItemInCart linea = agregarItem(cart, p, 1);
        UpdateCartItemQuantityRequest dto = actualizarDto(1L, 10);

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(1L, VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(10L);

        // Act
        cartService.updateCartItemQuantity(dto, c);

        // Assert
        assertEquals(10, linea.getQuantity());
        verify(cartRepository).save(cart);
    }

    //  removeProductFromCart
    @Test
    @DisplayName("When removing a product for a customer without a cart, it throws CarritoDeClienteNoEncontrado")
    void removeProductFromCart_noCart_throwsCustomerCartNotFound() {
        // Arrange
        Customer c = generarClienteFake();
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> cartService.removeProductFromCart(1L, c));
    }

    @Test
    @DisplayName("When removing a product that is not in the cart, it throws ProductoNoEnCarritoException")
    void removeProductFromCart_productNotInCart_throwsProductNotInCart() {
        // Arrange
        Customer c = generarClienteFake();
        Product enCarrito = generarProductoFake(p -> p.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, enCarrito, 2);

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));

        // Act + Assert
        assertThrows(ProductoNoEnCarritoException.class,
                () -> cartService.removeProductFromCart(99L, c));
        assertEquals(1, cart.getItems().size());   // nothing was touched
    }

    @Test
    @DisplayName("When removing a product, it disappears from the cart and the other lines are kept")
    void removeProductFromCart_exists_removesOnlyThatLine() {
        // Arrange
        Customer c = generarClienteFake();
        Product aBorrar = generarProductoFake(p -> p.setId(1L));
        Product otro = generarProductoFake(p -> p.setId(2L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        ItemInCart lineaBorrada = agregarItem(cart, aBorrar, 2);
        ItemInCart lineaOtra = agregarItem(cart, otro, 1);

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));

        // Act
        cartService.removeProductFromCart(1L, c);

        // Assert
        assertEquals(1, cart.getItems().size());
        assertFalse(cart.getItems().contains(lineaBorrada));
        assertTrue(cart.getItems().contains(lineaOtra));
        verify(cartRepository, never()).save(any());   // relies on orphanRemoval
    }

    //  getCartByCustomer
    @Test
    @DisplayName("When getting the cart of a customer without a cart, it throws CarritoNoEncontrado")
    void getCartByCustomer_notFound_throwsCartNotFound() {
        // Arrange
        Customer c = generarClienteFake();
        when(cartRepository.findByCustomerWithItems(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoNoEncontrado.class,
                () -> cartService.getCartByCustomer(c));
    }


    //  getCartByCustomerId
    @Test
    @DisplayName("When getting the cart by the id of a customer without a cart, it throws CarritoNoEncontrado")
    void getCartByCustomerId_notFound_throwsCartNotFound() {
        // Arrange
        when(cartRepository.findByCustomerId(5L)).thenReturn(Optional.empty());

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
        Customer c = generarClienteFake();
        Product p = generarProductoFake();
        AddCartItemRequest dto = agregarDto(p.getId(), 2);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Cart.class)))
                .thenThrow(new DataIntegrityViolationException("unique cart per customer"));

        // Act + Assert
        assertThrows(ConflictoConcurrenteException.class,
                () -> cartService.addOrUpdateCartItem(dto, c));
    }

    @Test
    @DisplayName("When saving the cart and the database rejects it for integrity, it throws ConflictoConcurrenteException")
    void addOrUpdateCartItem_dataIntegrityWhenSaving_throwsConcurrentConflict() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake();
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        AddCartItemRequest dto = agregarDto(p.getId(), 1);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(virtualCodeRepository.countByProductIdAndStatus(p.getId(), VirtualCode.CodeStatus.AVAILABLE))
                .thenReturn(10L);
        when(cartRepository.saveAndFlush(cart))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        // Act + Assert
        assertThrows(ConflictoConcurrenteException.class,
                () -> cartService.addOrUpdateCartItem(dto, c));
    }

    @Test
    @DisplayName("When the resulting quantity overflows an int, it throws ArithmeticException before validating stock")
    void addOrUpdateCartItem_quantityOverflow_throwsArithmeticException() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake();
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, p, Integer.MAX_VALUE);
        AddCartItemRequest dto = agregarDto(p.getId(), 1);

        when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));

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
        Customer c = generarClienteFake();
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, generarProductoFake(p -> p.setId(1L)), 2);
        agregarItem(cart, generarProductoFake(p -> p.setId(2L)), 1);

        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));

        // Act
        cartService.clearCart(c);

        // Assert
        assertTrue(cart.getItems().isEmpty());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    @DisplayName("When clearing the cart of a customer without a cart, it throws CarritoDeClienteNoEncontrado")
    void clearCart_noCart_throwsCustomerCartNotFound() {
        // Arrange
        Customer c = generarClienteFake();
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CarritoDeClienteNoEncontrado.class,
                () -> cartService.clearCart(c));
    }

    //  getCartByCustomer / getCartByCustomerId (success)
    @Test
    @DisplayName("When getting an existing cart, it returns the DTO and resolves each product's image URL")
    void getCartByCustomer_exists_returnsDtoAndResolvesImageUrls() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake(prod -> prod.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, p, 2);

        when(cartRepository.findByCustomerWithItems(c)).thenReturn(Optional.of(cart));

        // Act
        CartResponse resultado = cartService.getCartByCustomer(c);

        // Assert
        assertNotNull(resultado);
        verify(storageService).getPublicUrl(p.getImageKey());
    }

    @Test
    @DisplayName("When getting a cart by customer id, it returns the DTO and resolves each product's image URL")
    void getCartByCustomerId_exists_returnsDtoAndResolvesImageUrls() {
        // Arrange
        Customer c = generarClienteFake();
        Product p = generarProductoFake(prod -> prod.setId(1L));
        Cart cart = generarCarritoFake(car -> car.setCustomer(c));
        agregarItem(cart, p, 2);

        when(cartRepository.findByCustomerId(c.getId())).thenReturn(Optional.of(cart));

        // Act
        CartResponse resultado = cartService.getCartByCustomerId(c.getId());

        // Assert
        assertNotNull(resultado);
        verify(storageService).getPublicUrl(p.getImageKey());
    }
}
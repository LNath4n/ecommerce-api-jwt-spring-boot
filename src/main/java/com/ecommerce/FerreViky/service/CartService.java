package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadExcedidaException;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadNoValida;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoDeClienteNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ConflictoConcurrenteException;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ProductoNoEnCarritoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.carrito.CarritoMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.models.ProductoEnCarrito;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Shopping cart service for adding, updating, and removing products, and
 * retrieving a customer's cart.
 * <p>
 * A product's stock is the number of virtual codes in the
 * {@link EstadoCodigo#DISPONIBLE} state.
 */
@Service
@AllArgsConstructor
public class CartService {

    private static final String CONCURRENT_CART_UPDATE_MESSAGE =
            "Another operation is updating your cart. Please try again.";

    private final CarritoRepository carritoRepository;
    private final ProductoRepository productoRepository;
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final StorageService storageService;

    /**
     * Adds a product to the customer's cart, or increases its quantity if it is
     * already there.
     *
     * <p>If the product is already in the cart, {@code addRequest.cantidad()} is
     * added to its current quantity; otherwise, it is added as a new item. In
     * either case, stock is checked against the resulting total quantity.</p>
     *
     * <p>The cart is locked for the duration of the transaction to serialize
     * concurrent requests from the same customer. The customer is provided by
     * {@code @AuthenticationPrincipal}, so no additional lookup is needed.</p>
     *
     * @param addRequest {@code idProducto} and {@code cantidad} to add
     * @param customer authenticated customer who owns the cart
     * @throws ProductoNoEncontradoException if the product does not exist or is inactive
     * @throws CantidadNoValida if the quantity is less than or equal to 0
     * @throws CantidadExcedidaException if the total quantity exceeds available stock
     * @throws ConflictoConcurrenteException if another operation updates the cart concurrently
     */
    @Transactional
    public void addOrUpdateCartItem(AgregarCarrito addRequest, Cliente customer) {
        Producto product = getActiveProduct(addRequest.idProducto(), addRequest.cantidad());
        Carrito cart = getOrCreateLockedCart(customer);

        Optional<ProductoEnCarrito> existingItem = findCartItem(cart, product.getId());

        int currentQuantity = existingItem.map(ProductoEnCarrito::getCantidad).orElse(0);
        int newQuantity = Math.addExact(currentQuantity, addRequest.cantidad());

        validateStockForAddition(product, currentQuantity, addRequest.cantidad());

        if (existingItem.isPresent()) {
            existingItem.get().setCantidad(newQuantity);
        } else {
            ProductoEnCarrito newCartItem = new ProductoEnCarrito();
            newCartItem.setCarrito(cart);
            newCartItem.setProducto(product);
            newCartItem.setCantidad(newQuantity);
            cart.getProductos().add(newCartItem);
        }

        try {
            carritoRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoConcurrenteException(CONCURRENT_CART_UPDATE_MESSAGE);
        }
    }

    /**
     * Sets the exact quantity of a product that is already in the cart.
     *
     * <p>Unlike {@link #addOrUpdateCartItem}, which adds to the current quantity,
     * this method <b>replaces</b> it. If the quantity is {@code 0}, the item is
     * removed from the cart (requires {@code orphanRemoval = true} on the
     * {@code Carrito → ProductoEnCarrito} relationship).</p>
     *
     * @param quantityRequest product ID and new quantity (≥ 0)
     * @param customer authenticated customer who owns the cart
     * @throws CarritoDeClienteNoEncontrado if the customer has no cart
     * @throws ProductoNoEnCarritoException if the product is not in the cart
     * @throws CantidadExcedidaException if the quantity exceeds available stock
     */
    @Transactional
    public void updateCartItemQuantity(ActualizarCantidad quantityRequest, Cliente customer) {
        Carrito cart = getLockedCartForUpdate(customer);

        ProductoEnCarrito cartItem = findCartItem(cart, quantityRequest.idProducto())
                .orElseThrow(() -> new ProductoNoEnCarritoException(quantityRequest.idProducto()));

        if (quantityRequest.cantidad() == 0) {
            cart.getProductos().remove(cartItem);
        } else {
            validateExactStock(cartItem.getProducto(), quantityRequest.cantidad());
            cartItem.setCantidad(quantityRequest.cantidad());
        }

        carritoRepository.save(cart);
    }

    /**
     * Completely removes a product from the cart, regardless of its quantity.
     *
     * <p>Requires {@code orphanRemoval = true} on the
     * {@code Carrito → ProductoEnCarrito} relationship to delete the row from
     * the database.</p>
     *
     * @param productId ID of the product to remove
     * @param customer authenticated customer who owns the cart
     * @throws CarritoDeClienteNoEncontrado if the customer has no cart
     * @throws ProductoNoEnCarritoException if the product is not in the cart
     */
    @Transactional
    public void removeProductFromCart(Long productId, Cliente customer) {
        Carrito cart = getLockedCartForUpdate(customer);

        ProductoEnCarrito cartItem = findCartItem(cart, productId)
                .orElseThrow(() -> new ProductoNoEnCarritoException(productId));

        cart.getProductos().remove(cartItem);
    }

    /**
     * Returns the authenticated customer's cart as a DTO.
     *
     * <p>Loads the products in a single query ({@code JOIN FETCH}) and runs in a
     * read-only transaction.</p>
     *
     * @param customer authenticated customer
     * @return a {@link CarritoResponseDTO} containing the cart and its products
     * @throws CarritoNoEncontrado if the customer has no cart
     */
    @Transactional(readOnly = true)
    public CarritoResponseDTO getCartByCustomer(Cliente customer) {
        Carrito cart = carritoRepository.findByClienteConProductos(customer)
                .orElseThrow(() -> new CarritoNoEncontrado(customer.getId()));

        return toResponse(cart);
    }

    /**
     * Returns a customer's cart by their ID.
     *
     * <p>Intended for admin queries that do not have the {@link Cliente} entity.
     * The read-only transaction keeps the JPA session open while the response is
     * being built.</p>
     *
     * @param customerId customer ID
     * @return a {@link CarritoResponseDTO} containing the cart and its products
     * @throws CarritoNoEncontrado if the customer has no cart
     */
    @Transactional(readOnly = true)
    public CarritoResponseDTO getCartByCustomerId(Long customerId) {
        Carrito cart = carritoRepository.findByClienteId(customerId)
                .orElseThrow(() -> new CarritoNoEncontrado(customerId));

        return toResponse(cart);
    }

    /**
     * Retrieves and locks the customer's cart ({@code FOR UPDATE}) until the
     * transaction ends, creating it if necessary.
     * <p>
     * If another request creates the cart concurrently, the unique constraint
     * detects the conflict. In Postgres, the transaction is aborted after this
     * error, so no further queries should be run within it.
     *
     * @param customer customer who owns the cart
     * @return the locked cart, either existing or newly created
     * @throws ConflictoConcurrenteException if another request creates the cart first
     */
    private Carrito getOrCreateLockedCart(Cliente customer) {
        return carritoRepository.findByClienteParaActualizar(customer)
                .orElseGet(() -> {
                    Carrito newCart = new Carrito();
                    newCart.setCliente(customer);
                    newCart.setFechaCreacion(LocalDateTime.now());
                    newCart.setProductos(new ArrayList<>());
                    try {
                        return carritoRepository.saveAndFlush(newCart);
                    } catch (DataIntegrityViolationException e) {
                        throw new ConflictoConcurrenteException(CONCURRENT_CART_UPDATE_MESSAGE);
                    }
                });
    }

    /**
     * Retrieves the customer's cart and locks it for updates.
     *
     * @param customer customer who owns the cart
     * @return the locked cart
     * @throws CarritoDeClienteNoEncontrado if the customer has no cart
     */
    private Carrito getLockedCartForUpdate(Cliente customer) {
        return carritoRepository.findByClienteParaActualizar(customer)
                .orElseThrow(() -> new CarritoDeClienteNoEncontrado(customer.getId()));
    }

    /**
     * Finds a product's line item in the cart.
     *
     * @param cart cart to search
     * @param productId product ID
     * @return the cart item, or empty if the product is not in the cart
     */
    private Optional<ProductoEnCarrito> findCartItem(Carrito cart, Long productId) {
        return cart.getProductos().stream()
                .filter(cartItem -> cartItem.getProducto().getId().equals(productId))
                .findFirst();
    }

    /**
     * Finds a product and checks that it exists, is active, and has a positive
     * requested quantity.
     *
     * <p>Stock is checked separately by {@link #validateStockForAddition(Producto, long, long)}
     * because adding to an existing line requires checking the resulting total.</p>
     *
     * @param productId product ID
     * @param requestedQuantity requested quantity
     * @return the product
     * @throws ProductoNoEncontradoException if the product does not exist or is inactive
     * @throws CantidadNoValida if the quantity is less than or equal to 0
     */
    private Producto getActiveProduct(Long productId, int requestedQuantity) {
        Producto product = productoRepository.findById(productId)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(productId));

        if (requestedQuantity <= 0)
            throw new CantidadNoValida(requestedQuantity);

        return product;
    }

    /**
     * Checks that enough stock is available to <b>add</b> units to a product
     * that may already be in the cart.
     *
     * <p>Stock is the number of virtual codes in the
     * {@link EstadoCodigo#DISPONIBLE} state. It is checked against
     * {@code currentQuantity + requestedQuantity}.</p>
     *
     * @param product product whose stock is being checked
     * @param currentQuantity units already in the customer's cart (0 if not present)
     * @param requestedQuantity units to add
     * @throws CantidadExcedidaException if the resulting total exceeds available stock
     */
    private void validateStockForAddition(Producto product, long currentQuantity, long requestedQuantity) {
        long availableStock = codigoVirtualRepository
                .countByProductoIdAndEstado(product.getId(), EstadoCodigo.DISPONIBLE);

        if (currentQuantity + requestedQuantity > availableStock)
            throw new CantidadExcedidaException(
                    product.getNombre(), availableStock, currentQuantity, requestedQuantity);
    }

    /**
     * Checks that enough stock is available to <b>set</b> a product's exact cart
     * quantity, replacing the current one.
     *
     * @param product product whose stock is being checked
     * @param desiredQuantity total quantity requested by the customer
     * @throws CantidadExcedidaException if the desired quantity exceeds available stock
     */
    private void validateExactStock(Producto product, long desiredQuantity) {
        long availableStock = codigoVirtualRepository
                .countByProductoIdAndEstado(product.getId(), EstadoCodigo.DISPONIBLE);

        if (desiredQuantity > availableStock)
            throw new CantidadExcedidaException(
                    product.getNombre(), availableStock, desiredQuantity);
    }

    /**
     * Removes all products from the customer's cart.
     *
     * <p>The cart itself is kept but left empty. This requires
     * {@code orphanRemoval = true} on the {@code Carrito → ProductoEnCarrito}
     * relationship to delete the rows.</p>
     *
     * @param customer authenticated customer who owns the cart
     * @throws CarritoDeClienteNoEncontrado if the customer has no cart
     */
    @Transactional
    public void clearCart(Cliente customer) {
        Carrito cart = getLockedCartForUpdate(customer);
        cart.getProductos().clear();
    }

    /**
     * Converts a cart to a DTO and resolves the public image URL for each
     * product.
     *
     * @param cart cart to convert
     * @return cart DTO with {@code imagenUrl} for each item
     */
    private CarritoResponseDTO toResponse(Carrito cart) {
        return CarritoMappers.toCarritoResponseDTO(
                cart, product -> storageService.getPublicUrl(product.getImagenKey()));
    }
}
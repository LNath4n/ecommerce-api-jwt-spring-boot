package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.UpdateCartItemQuantityRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.AddCartItemRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartResponse;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.QuantityExceededException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.InvalidQuantityException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.CustomerCartNotFoundException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.CartNotFoundException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.ConcurrentCartUpdateException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.ProductNotInCartException;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.ProductNotFoundException;
import com.ecommerce.FerreViky.mapper.cart.CartMapper;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.models.ItemInCart;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
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
 * {@link CodeStatus#AVAILABLE} state.
 */
@Service
@AllArgsConstructor
public class CartService {

    private static final String CONCURRENT_CART_UPDATE_MESSAGE =
            "Another operation is updating your cart. Please try again.";

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final VirtualCodeRepository virtualCodeRepository;
    private final StorageService storageService;

    /**
     * Adds a product to the customer's cart, or increases its quantity if it is
     * already there.
     *
     * <p>If the product is already in the cart, {@code addRequest.quantity()} is
     * added to its current quantity; otherwise, it is added as a new item. In
     * either case, stock is checked against the resulting total quantity.</p>
     *
     * <p>The cart is locked for the duration of the transaction to serialize
     * concurrent requests from the same customer. The customer is provided by
     * {@code @AuthenticationPrincipal}, so no additional lookup is needed.</p>
     *
     * @param addRequest {@code productId} and {@code quantity} to add
     * @param customer authenticated customer who owns the cart
     * @throws ProductNotFoundException if the product does not exist or is inactive
     * @throws InvalidQuantityException if the quantity is less than or equal to 0
     * @throws QuantityExceededException if the total quantity exceeds available stock
     * @throws ConcurrentCartUpdateException if another operation updates the cart concurrently
     */
    @Transactional
    public void addOrUpdateCartItem(AddCartItemRequest addRequest, Customer customer) {
        Product product = getActiveProduct(addRequest.productId(), addRequest.quantity());
        Cart cart = getOrCreateLockedCart(customer);

        Optional<ItemInCart> existingItem = findCartItem(cart, product.getId());

        int currentQuantity = existingItem.map(ItemInCart::getQuantity).orElse(0);
        int newQuantity = Math.addExact(currentQuantity, addRequest.quantity());

        validateStockForAddition(product, currentQuantity, addRequest.quantity());

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(newQuantity);
        } else {
            ItemInCart newCartItem = new ItemInCart();
            newCartItem.setCart(cart);
            newCartItem.setProduct(product);
            newCartItem.setQuantity(newQuantity);
            cart.getItems().add(newCartItem);
        }

        try {
            cartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrentCartUpdateException(CONCURRENT_CART_UPDATE_MESSAGE);
        }
    }

    /**
     * Sets the exact quantity of a product that is already in the cart.
     *
     * <p>Unlike {@link #addOrUpdateCartItem}, which adds to the current quantity,
     * this method <b>replaces</b> it. If the quantity is {@code 0}, the item is
     * removed from the cart (requires {@code orphanRemoval = true} on the
     * {@code Cart → ItemInCart} relationship).</p>
     *
     * @param quantityRequest product ID and new quantity (≥ 0)
     * @param customer authenticated customer who owns the cart
     * @throws CustomerCartNotFoundException if the customer has no cart
     * @throws ProductNotInCartException if the product is not in the cart
     * @throws QuantityExceededException if the quantity exceeds available stock
     */
    @Transactional
    public void updateCartItemQuantity(UpdateCartItemQuantityRequest quantityRequest, Customer customer) {
        Cart cart = getLockedCartForUpdate(customer);

        ItemInCart cartItem = findCartItem(cart, quantityRequest.productId())
                .orElseThrow(() -> new ProductNotInCartException(quantityRequest.productId()));

        if (quantityRequest.quantity() == 0) {
            cart.getItems().remove(cartItem);
        } else {
            validateExactStock(cartItem.getProduct(), quantityRequest.quantity());
            cartItem.setQuantity(quantityRequest.quantity());
        }

        cartRepository.save(cart);
    }

    /**
     * Completely removes a product from the cart, regardless of its quantity.
     *
     * <p>Requires {@code orphanRemoval = true} on the
     * {@code Cart → ItemInCart} relationship to delete the row from
     * the database.</p>
     *
     * @param productId ID of the product to remove
     * @param customer authenticated customer who owns the cart
     * @throws CustomerCartNotFoundException if the customer has no cart
     * @throws ProductNotInCartException if the product is not in the cart
     */
    @Transactional
    public void removeProductFromCart(Long productId, Customer customer) {
        Cart cart = getLockedCartForUpdate(customer);

        ItemInCart cartItem = findCartItem(cart, productId)
                .orElseThrow(() -> new ProductNotInCartException(productId));

        cart.getItems().remove(cartItem);
    }

    /**
     * Returns the authenticated customer's cart as a DTO.
     *
     * <p>Loads the products in a single query ({@code JOIN FETCH}) and runs in a
     * read-only transaction.</p>
     *
     * @param customer authenticated customer
     * @return a {@link CartResponse} containing the cart and its products
     * @throws CartNotFoundException if the customer has no cart
     */
    @Transactional(readOnly = true)
    public CartResponse getCartByCustomer(Customer customer) {
        Cart cart = cartRepository.findByCustomerWithItems(customer)
                .orElseThrow(() -> new CartNotFoundException(customer.getId()));

        return toResponse(cart);
    }

    /**
     * Returns a customer's cart by their ID.
     *
     * <p>Intended for admin queries that do not have the {@link Customer} entity.
     * The read-only transaction keeps the JPA session open while the response is
     * being built.</p>
     *
     * @param customerId customer ID
     * @return a {@link CartResponse} containing the cart and its products
     * @throws CartNotFoundException if the customer has no cart
     */
    @Transactional(readOnly = true)
    public CartResponse getCartByCustomerId(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

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
     * @throws ConcurrentCartUpdateException if another request creates the cart first
     */
    private Cart getOrCreateLockedCart(Customer customer) {
        return cartRepository.findByCustomerForUpdate(customer)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setCustomer(customer);
                    newCart.setCreatedAt(LocalDateTime.now());
                    newCart.setItems(new ArrayList<>());
                    try {
                        return cartRepository.saveAndFlush(newCart);
                    } catch (DataIntegrityViolationException e) {
                        throw new ConcurrentCartUpdateException(CONCURRENT_CART_UPDATE_MESSAGE);
                    }
                });
    }

    /**
     * Retrieves the customer's cart and locks it for updates.
     *
     * @param customer customer who owns the cart
     * @return the locked cart
     * @throws CustomerCartNotFoundException if the customer has no cart
     */
    private Cart getLockedCartForUpdate(Customer customer) {
        return cartRepository.findByCustomerForUpdate(customer)
                .orElseThrow(() -> new CustomerCartNotFoundException(customer.getId()));
    }

    /**
     * Finds a product's line item in the cart.
     *
     * @param cart cart to search
     * @param productId product ID
     * @return the cart item, or empty if the product is not in the cart
     */
    private Optional<ItemInCart> findCartItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(cartItem -> cartItem.getProduct().getId().equals(productId))
                .findFirst();
    }

    /**
     * Finds a product and checks that it exists, is active, and has a positive
     * requested quantity.
     *
     * <p>Stock is checked separately by {@link #validateStockForAddition(Product, long, long)}
     * because adding to an existing line requires checking the resulting total.</p>
     *
     * @param productId product ID
     * @param requestedQuantity requested quantity
     * @return the product
     * @throws ProductNotFoundException if the product does not exist or is inactive
     * @throws InvalidQuantityException if the quantity is less than or equal to 0
     */
    private Product getActiveProduct(Long productId, int requestedQuantity) {
        Product product = productRepository.findById(productId)
                .filter(Product::getActive)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (requestedQuantity <= 0)
            throw new InvalidQuantityException(requestedQuantity);

        return product;
    }

    /**
     * Checks that enough stock is available to <b>add</b> units to a product
     * that may already be in the cart.
     *
     * <p>Stock is the number of virtual codes in the
     * {@link CodeStatus#AVAILABLE} state. It is checked against
     * {@code currentQuantity + requestedQuantity}.</p>
     *
     * @param product product whose stock is being checked
     * @param currentQuantity units already in the customer's cart (0 if not present)
     * @param requestedQuantity units to add
     * @throws QuantityExceededException if the resulting total exceeds available stock
     */
    private void validateStockForAddition(Product product, long currentQuantity, long requestedQuantity) {
        long availableStock = virtualCodeRepository
                .countByProductIdAndStatus(product.getId(), CodeStatus.AVAILABLE);

        if (currentQuantity + requestedQuantity > availableStock)
            throw new QuantityExceededException(
                    product.getName(), availableStock, currentQuantity, requestedQuantity);
    }

    /**
     * Checks that enough stock is available to <b>set</b> a product's exact cart
     * quantity, replacing the current one.
     *
     * @param product product whose stock is being checked
     * @param desiredQuantity total quantity requested by the customer
     * @throws QuantityExceededException if the desired quantity exceeds available stock
     */
    private void validateExactStock(Product product, long desiredQuantity) {
        long availableStock = virtualCodeRepository
                .countByProductIdAndStatus(product.getId(), CodeStatus.AVAILABLE);

        if (desiredQuantity > availableStock)
            throw new QuantityExceededException(
                    product.getName(), availableStock, desiredQuantity);
    }

    /**
     * Removes all products from the customer's cart.
     *
     * <p>The cart itself is kept but left empty. This requires
     * {@code orphanRemoval = true} on the {@code Cart → ItemInCart}
     * relationship to delete the rows.</p>
     *
     * @param customer authenticated customer who owns the cart
     * @throws CustomerCartNotFoundException if the customer has no cart
     */
    @Transactional
    public void clearCart(Customer customer) {
        Cart cart = getLockedCartForUpdate(customer);
        cart.getItems().clear();
    }

    /**
     * Converts a cart to a DTO and resolves the public image URL for each
     * product.
     *
     * @param cart cart to convert
     * @return cart DTO with {@code imageUrl} for each item
     */
    private CartResponse toResponse(Cart cart) {
        return CartMapper.toCartResponse(
                cart, product -> storageService.getPublicUrl(product.getImageKey()));
    }
}
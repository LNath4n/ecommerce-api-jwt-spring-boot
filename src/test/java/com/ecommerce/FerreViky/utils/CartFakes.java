package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.AddCartItemRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartItemResponse;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartResponse;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.UpdateCartItemQuantityRequest;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.ItemInCart;
import com.ecommerce.FerreViky.models.Product;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CartFakes {

    private static final Faker faker = new Faker();

    private CartFakes() {}

    public static Cart fakeCart() {
        return fakeCart(cart -> {});
    }

    public static Cart fakeCart(Consumer<Cart> changes) {
        Cart cart = new Cart();
        cart.setId(faker.number().numberBetween(1L, 100_000L));
        cart.setCustomer(CustomerFakes.fakeCustomer());
        cart.setItems(new ArrayList<>());
        cart.setCreatedAt(LocalDateTime.now());

        changes.accept(cart);
        return cart;
    }

    public static ItemInCart addCartItem(Cart cart, Product product, int quantity) {
        ItemInCart item = new ItemInCart();
        item.setId(faker.number().numberBetween(1L, 100_000L));
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        cart.getItems().add(item);
        return item;
    }

    public static AddCartItemRequest newAddCartItemRequest(Long productId, int quantity) {
        return new AddCartItemRequest(productId, quantity);
    }

    public static UpdateCartItemQuantityRequest newUpdateCartItemQuantityRequest(
            Long productId, int quantity) {
        return new UpdateCartItemQuantityRequest(productId, quantity);
    }

    public static class CartResponseBuilder {
        public List<CartItemResponse> items;
        public LocalDateTime createdAt;
        public BigDecimal subtotal;

        public CartResponse build() {
            return new CartResponse(items, createdAt, subtotal);
        }
    }

    public static CartItemResponse expectedCartItem(Product product, int quantity, String imageUrl) {
        return new CartItemResponse(
                product.getId(), product.getSlug(), product.getName(), product.getSku(),
                product.getPlatform(), quantity, product.getSalePrice(), imageUrl);
    }

    public static CartResponse expectedCart(Cart cart, Function<Product, String> imageUrlFor) {
        return expectedCart(cart, imageUrlFor, builder -> {});
    }

    public static CartResponse expectedCart(
            Cart cart, Function<Product, String> imageUrlFor, Consumer<CartResponseBuilder> changes) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemInCart cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            items.add(expectedCartItem(product, cartItem.getQuantity(), imageUrlFor.apply(product)));
            subtotal = subtotal.add(
                    product.getSalePrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        CartResponseBuilder builder = new CartResponseBuilder();
        builder.items = items;
        builder.createdAt = cart.getCreatedAt();
        builder.subtotal = subtotal;

        changes.accept(builder);
        return builder.build();
    }
}

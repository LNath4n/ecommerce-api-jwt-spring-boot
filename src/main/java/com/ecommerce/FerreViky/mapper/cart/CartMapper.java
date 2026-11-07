package com.ecommerce.FerreViky.mapper.cart;

import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartItemResponse;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartResponse;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.ItemInCart;
import com.ecommerce.FerreViky.models.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public class CartMapper {

    public static CartItemResponse toCartItemResponse(
            ItemInCart cartItem, Function<Product, String> imageUrlResolver) {
        Product product = cartItem.getProduct();
        return new CartItemResponse(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getSku(),
                product.getPlatform(),
                cartItem.getQuantity(),
                product.getSalePrice(),
                imageUrlResolver.apply(product)
        );
    }

    public static CartResponse toCartResponse(
            Cart cart, Function<Product, String> imageUrlResolver) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(cartItem -> toCartItemResponse(cartItem, imageUrlResolver))
                .toList();

        BigDecimal subtotal = items.stream()
                .map(item -> item.salePrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
                items,
                cart.getCreatedAt(),
                subtotal
        );
    }
}
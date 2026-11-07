package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.ItemInCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<ItemInCart, Long> {
    @Modifying
    @Query("DELETE FROM ItemInCart cartItem WHERE cartItem.product.id = :productId")
    void deleteAllByProductId(@Param("productId") Long productId);
}
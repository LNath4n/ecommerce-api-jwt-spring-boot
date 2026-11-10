package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.models.OrderItem;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.models.Product;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Consumer;

public final class OrderFakes {

    private static final Faker faker = new Faker();

    private OrderFakes() {}

    public static Orders fakeOrder() {
        return fakeOrder(order -> {});
    }

    public static Orders fakeOrder(Consumer<Orders> changes) {
        Orders order = new Orders();
        order.setId(faker.number().numberBetween(1L, 100_000L));
        order.setBuyer(CustomerFakes.fakeCustomer());
        order.setTotalAmount(new BigDecimal("210.00"));
        order.setStatus(Orders.OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        order.setItems(new ArrayList<>());

        changes.accept(order);
        return order;
    }

    public static OrderItem addOrderItem(Orders order, Product product, int quantity, BigDecimal unitPrice) {
        OrderItem item = new OrderItem();
        item.setOrders(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        order.getItems().add(item);
        return item;
    }
}

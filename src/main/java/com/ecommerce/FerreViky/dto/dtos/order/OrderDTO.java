package com.ecommerce.FerreViky.dto.dtos.order;

import com.ecommerce.FerreViky.models.Orders.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDTO {

    public record OrderSummary(Long id, LocalDateTime createdAt,
                               BigDecimal total, OrderStatus status) {}

    public record DeliveredItem(Long productId, String name, int quantity,
                                BigDecimal unitPrice, String redemptionInstructions,
                                List<String> codes) {}

    public record OrderDetail(Long id, LocalDateTime createdAt, BigDecimal total,
                              OrderStatus status, List<DeliveredItem> items) {}
}
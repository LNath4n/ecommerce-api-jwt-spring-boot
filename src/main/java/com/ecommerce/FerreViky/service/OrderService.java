package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.DeliveredItem;
import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.OrderDetail;
import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.OrderSummary;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.OrderNotFoundException;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.repository.OrdersRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides read operations for orders belonging to a customer.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrdersRepository ordersRepository;
    private final VirtualCodeRepository virtualCodeRepository;

    /**
     * Retrieves all orders for a customer, ordered from newest to oldest.
     *
     * @param customer customer whose orders are requested
     * @return the customer's order summaries
     */
    @Transactional(readOnly = true)
    public List<OrderSummary> myOrders(Customer customer) {
        log.debug("Fetching orders for customer {}", customer.getId());
        List<OrderSummary> orders = ordersRepository.findByBuyerOrderByCreatedAtDesc(customer).stream()
                .map(o -> new OrderSummary(o.getId(), o.getCreatedAt(),
                        o.getTotalAmount(), o.getStatus()))
                .toList();
        log.debug("Found {} orders for customer {}", orders.size(), customer.getId());
        return orders;
    }

    /**
     * Retrieves the details of an order belonging to a customer.
     *
     * <p>Sold redemption codes are included only when the order is paid.</p>
     *
     * @param orderId ID of the order to retrieve
     * @param customer customer who owns the order
     * @return the order details and delivered items
     * @throws OrderNotFoundException if the order does not exist or does not belong to the customer
     */
    @Transactional(readOnly = true)
    public OrderDetail myOrder(Long orderId, Customer customer) {
        log.debug("Fetching order {} for customer {}", orderId, customer.getId());
        Orders o = ordersRepository.findByIdAndBuyer(orderId, customer)
                .orElseThrow(() -> {
                    log.warn("Order {} not found for customer {}", orderId, customer.getId());
                    return new OrderNotFoundException(orderId);
                });

        // Solo se entregan códigos si el pedido está pagado
        Map<Long, List<String>> codesByProduct = o.getStatus() == OrderStatus.PAID
                ? virtualCodeRepository.findByOrdersId(orderId).stream()
                .filter(c -> c.getStatus() == CodeStatus.SOLD)
                .collect(Collectors.groupingBy(
                        c -> c.getProduct().getId(),
                        Collectors.mapping(VirtualCode::getCode, Collectors.toList())))
                : Map.of();
        // Only counts are logged, never the codes themselves
        log.debug("Order {} is {}, codes delivered for {} products",
                orderId, o.getStatus(), codesByProduct.size());

        List<DeliveredItem> items = o.getItems().stream()
                .map(i -> new DeliveredItem(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getProduct().getRedemptionInstructions(),
                        codesByProduct.getOrDefault(i.getProduct().getId(), List.of())))
                .toList();

        log.debug("Order {} detail built with {} items", orderId, items.size());
        return new OrderDetail(o.getId(), o.getCreatedAt(), o.getTotalAmount(),
                o.getStatus(), items);
    }
}
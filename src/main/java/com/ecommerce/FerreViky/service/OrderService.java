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
        return ordersRepository.findByBuyerOrderByCreatedAtDesc(customer).stream()
                .map(o -> new OrderSummary(o.getId(), o.getCreatedAt(),
                        o.getTotalAmount(), o.getStatus()))
                .toList();
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
        Orders o = ordersRepository.findByIdAndBuyer(orderId, customer)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Solo se entregan códigos si el pedido está pagado
        Map<Long, List<String>> codesByProduct = o.getStatus() == OrderStatus.PAID
                ? virtualCodeRepository.findByOrdersId(orderId).stream()
                .filter(c -> c.getStatus() == CodeStatus.SOLD)
                .collect(Collectors.groupingBy(
                        c -> c.getProduct().getId(),
                        Collectors.mapping(VirtualCode::getCode, Collectors.toList())))
                : Map.of();

        List<DeliveredItem> items = o.getItems().stream()
                .map(i -> new DeliveredItem(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getProduct().getRedemptionInstructions(),
                        codesByProduct.getOrDefault(i.getProduct().getId(), List.of())))
                .toList();

        return new OrderDetail(o.getId(), o.getCreatedAt(), o.getTotalAmount(),
                o.getStatus(), items);
    }
}

package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {
    Optional<Orders> findByPaypalOrderIdAndBuyer(String paypalOrderId, Customer buyer);

    List<Orders> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    List<Orders> findByBuyerAndStatus(Customer buyer, OrderStatus status);
    List<Orders> findByStatus(OrderStatus status);

    List<Orders> findByBuyerOrderByCreatedAtDesc(Customer buyer);
    Optional<Orders> findByIdAndBuyer(Long id, Customer buyer);
}
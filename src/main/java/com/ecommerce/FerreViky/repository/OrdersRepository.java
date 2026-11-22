package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Orders o WHERE o.id = :id")
    Optional<Orders> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o.id FROM Orders o WHERE o.buyer = :buyer AND o.status = :status")
    List<Long> findIdsByBuyerAndStatus(@Param("buyer") Customer buyer,
                                       @Param("status") OrderStatus status);

    Optional<Orders> findByPaypalOrderId(String paypalOrderId);
}
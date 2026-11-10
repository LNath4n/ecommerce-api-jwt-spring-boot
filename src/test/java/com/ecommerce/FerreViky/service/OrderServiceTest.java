package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.OrderSummary;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.repository.OrdersRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static com.ecommerce.FerreViky.utils.ProductFakes.*;
import static com.ecommerce.FerreViky.utils.VirtualCodeFakes.*;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.DeliveredItem;
import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.OrderDetail;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.ecommerce.FerreViky.utils.CustomerFakes.*;
import static com.ecommerce.FerreViky.utils.OrderFakes.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Unit tests")
class OrderServiceTest {

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private VirtualCodeRepository virtualCodeRepository;

    @InjectMocks
    private OrderService orderService;


    @Test
    @DisplayName("When the customer has no orders, it returns an empty list")
    void myOrders_noOrders_returnsEmptyList() {
        // Arrange
        Customer c = fakeCustomer();
        // Act
        when(ordersRepository.findByBuyerOrderByCreatedAtDesc(c))
                .thenReturn(Collections.emptyList());
        List<OrderSummary> resultado = orderService.myOrders(c);

        // Assert
        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("When the customer has orders, it maps each one to an OrderSummary with id, date, total and status")
    void myOrders_withOrders_mapsToSummaries() {
        // Arrange
        Customer c = fakeCustomer();
        Orders o1 = fakeOrder(orders -> {
            orders.setId(1L);
            orders.setBuyer(c);
        });
        Orders o2 = fakeOrder(orders -> {
            orders.setId(2L);
            orders.setBuyer(c);
        });

        when(ordersRepository.findByBuyerOrderByCreatedAtDesc(c))
                .thenReturn(List.of(o1, o2));

        // Act
        List<OrderSummary> resultado = orderService.myOrders(c);

        // Assert
        assertEquals(2, resultado.size());

        OrderSummary s1 = resultado.get(0);
        assertEquals(o1.getId(), s1.id());
        assertEquals(o1.getCreatedAt(), s1.createdAt());
        assertEquals(o1.getTotalAmount(), s1.total());
        assertEquals(o1.getStatus(), s1.status());

        OrderSummary s2 = resultado.get(1);
        assertEquals(o2.getId(), s2.id());
        assertEquals(o2.getCreatedAt(), s2.createdAt());
        assertEquals(o2.getTotalAmount(), s2.total());
        assertEquals(o2.getStatus(), s2.status());
    }

    @Test
    @DisplayName("When the customer has several orders, it keeps the order returned by the repository")
    void myOrders_multipleOrders_preservesRepositoryOrder() {
        //Comprobamos que se ordene por fechas no por IDS
        // Arrange
        Customer c = fakeCustomer();
        Orders masReciente = fakeOrder(o -> {
            o.setId(3L);
            o.setBuyer(c);
            o.setCreatedAt(LocalDateTime.of(2026, 11, 8, 12, 0));
        });
        Orders intermedia = fakeOrder(o -> {
            o.setId(1L);
            o.setBuyer(c);
            o.setCreatedAt(LocalDateTime.of(2026, 11, 5, 12, 0));
        });
        Orders masAntigua = fakeOrder(o -> {
            o.setId(2L);
            o.setBuyer(c);
            o.setCreatedAt(LocalDateTime.of(2026, 11, 1, 12, 0));
        });

        // el repositorio ya las devuelve ordenadas por fecha DESC
        when(ordersRepository.findByBuyerOrderByCreatedAtDesc(c))
                .thenReturn(List.of(masReciente, intermedia, masAntigua));

        // Act
        List<OrderSummary> resultado = orderService.myOrders(c);

        // Assert
        assertEquals(3, resultado.size());
        assertEquals(List.of(3L, 1L, 2L),
                resultado.stream().map(OrderSummary::id).toList());
    }

    //MyOrder
    @Test
    @DisplayName("When the order does not exist or belongs to another customer, it throws OrderNotFoundException")
    void myOrder_notFound_throwsOrderNotFound() {
        // Arrange
        Customer customer = fakeCustomer();
        Long orderId = 99L;

        when(ordersRepository.findByIdAndBuyer(orderId, customer))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(OrderExceptions.OrderNotFoundException.class,
                () -> orderService.myOrder(orderId, customer));
    }

    @Test
    @DisplayName("When the order is not found, it does not query virtual codes")
    void myOrder_notFound_doesNotQueryCodes() {
        // Arrange
        Customer customer = fakeCustomer();
        when(ordersRepository.findByIdAndBuyer(99L, customer)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(OrderExceptions.OrderNotFoundException.class,
                () -> orderService.myOrder(99L, customer));
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("When the order is PAID, it delivers the SOLD codes grouped by product")
    void myOrder_paid_deliversSoldCodesGroupedByProduct() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(prod -> prod.setId(1L));
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setStatus(OrderStatus.PAID);
        });
        addOrderItem(order, p, 2, p.getSalePrice());

        VirtualCode c1 = fakeVirtualCode(p, "AAA", vc -> vc.setStatus(CodeStatus.SOLD));
        VirtualCode c2 = fakeVirtualCode(p, "BBB", vc -> vc.setStatus(CodeStatus.SOLD));

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(List.of(c1, c2));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertEquals(1, resultado.items().size());
        assertEquals(List.of("AAA", "BBB"), resultado.items().get(0).codes());
    }

    @Test
    @DisplayName("When the order is PAID, it ignores codes that are not SOLD")
    void myOrder_paid_ignoresNonSoldCodes() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(prod -> prod.setId(1L));
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setStatus(OrderStatus.PAID);
        });
        addOrderItem(order, p, 1, p.getSalePrice());

        VirtualCode sold = fakeVirtualCode(p, "SOLD-1", vc -> vc.setStatus(CodeStatus.SOLD));
        VirtualCode available = fakeVirtualCode(p, "AVAIL-1"); // AVAILABLE por default

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(List.of(sold, available));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertEquals(List.of("SOLD-1"), resultado.items().get(0).codes());
    }

    @Test
    @DisplayName("When the order is PAID and a product has no codes, its item is delivered with an empty list")
    void myOrder_paid_productWithoutCodes_returnsEmptyCodeList() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(prod -> prod.setId(1L));
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setStatus(OrderStatus.PAID);
        });
        addOrderItem(order, p, 1, p.getSalePrice());

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(List.of());

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertEquals(1, resultado.items().size());
        assertTrue(resultado.items().get(0).codes().isEmpty());
    }

    @Test
    @DisplayName("When the order is PAID and has several products, each item receives only its own codes")
    void myOrder_paid_multipleProducts_eachItemGetsItsOwnCodes() {
        // Arrange
        Customer c = fakeCustomer();
        Product p1 = fakeProduct(prod -> prod.setId(1L));
        Product p2 = fakeProduct(prod -> prod.setId(2L));
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setStatus(OrderStatus.PAID);
        });
        addOrderItem(order, p1, 1, p1.getSalePrice());
        addOrderItem(order, p2, 2, p2.getSalePrice());

        VirtualCode a = fakeVirtualCode(p1, "P1-A", vc -> vc.setStatus(CodeStatus.SOLD));
        VirtualCode b = fakeVirtualCode(p2, "P2-A", vc -> vc.setStatus(CodeStatus.SOLD));
        VirtualCode d = fakeVirtualCode(p2, "P2-B", vc -> vc.setStatus(CodeStatus.SOLD));

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(List.of(a, b, d));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertEquals(List.of("P1-A"), resultado.items().get(0).codes());
        assertEquals(List.of("P2-A", "P2-B"), resultado.items().get(1).codes());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("When the order is not PAID, it delivers no codes and does not query them")
    void myOrder_notPaid_deliversNoCodes(OrderStatus status) {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(prod -> prod.setId(1L));
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setStatus(status);
        });
        addOrderItem(order, p, 1, p.getSalePrice());

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertTrue(resultado.items().get(0).codes().isEmpty());
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("When building the detail, it maps product id, name, quantity, unit price and redemption instructions")
    void myOrder_exists_mapsItemFields() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(prod -> prod.setId(7L));
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
        });
        addOrderItem(order, p, 3, new BigDecimal("199.99"));

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        DeliveredItem item = resultado.items().get(0);
        assertEquals(7L, item.productId());
        assertEquals(p.getName(), item.name());
        assertEquals(3, item.quantity());
        assertEquals(new BigDecimal("199.99"), item.unitPrice());
        assertEquals(p.getRedemptionInstructions(), item.redemptionInstructions());
    }

    @Test
    @DisplayName("When building the detail, it maps the order id, creation date, total amount and status")
    void myOrder_exists_mapsOrderFields() {
        // Arrange
        Customer c = fakeCustomer();
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setTotalAmount(new BigDecimal("630.00"));
            o.setStatus(OrderStatus.PENDING_PAYMENT);
            o.setCreatedAt(LocalDateTime.of(2026, 11, 8, 12, 0));
        });

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertEquals(10L, resultado.id());
        assertEquals(order.getCreatedAt(), resultado.createdAt());
        assertEquals(order.getTotalAmount(), resultado.total());
        assertEquals(OrderStatus.PENDING_PAYMENT, resultado.status());
    }

    @Test
    @DisplayName("When the order has no items, it returns a detail with an empty item list")
    void myOrder_noItems_returnsEmptyItems() {
        // Arrange
        Customer c = fakeCustomer();
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
        }); // items vacío por default

        when(ordersRepository.findByIdAndBuyer(10L, c)).thenReturn(Optional.of(order));

        // Act
        OrderDetail resultado = orderService.myOrder(10L, c);

        // Assert
        assertTrue(resultado.items().isEmpty());
    }

}
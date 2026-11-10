package com.ecommerce.FerreViky.service;

import static com.ecommerce.FerreViky.utils.CartFakes.*;

import static com.ecommerce.FerreViky.utils.CustomerFakes.*;
import static com.ecommerce.FerreViky.utils.OrderFakes.*;
import static com.ecommerce.FerreViky.utils.ProductFakes.*;
import static com.ecommerce.FerreViky.utils.VirtualCodeFakes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.*;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.*;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.ProductNotFoundException;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.OrdersRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckoutService - Unit tests")
class CheckoutServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private VirtualCodeRepository virtualCodeRepository;
    @Mock
    private PaypalService paypalService;
    @Mock
    private PlatformTransactionManager transactionManager;

    // Se construye a mano en setUp (necesita un TransactionTemplate real), por eso no lleva @InjectMocks
    private CheckoutService checkoutService;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    //paypal me da un JsonNode

    /** requestId que el service manda a PayPal al capturar la orden 10 (para idempotencia). */
    private static final String CAPTURE_REQUEST_ID = "capture-order-10";

    @BeforeEach
    void setUp() {
        //No puedo mockear el Template pero si PlatformTransactionManager
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        checkoutService = new CheckoutService(
                cartRepository, ordersRepository, virtualCodeRepository, paypalService, tx);
        ReflectionTestUtils.setField(checkoutService, "reservationMinutes", 15);
    }

    private JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    //  checkout

    @Test
    @DisplayName("When the customer has no cart, it throws CustomerCartNotFoundException")
    void checkout_noCart_throwsCustomerCartNotFound() {
        // Arrange
        Customer c = fakeCustomer();

        when(cartRepository.findByCustomerForUpdate(c))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CustomerCartNotFoundException.class,
                () -> checkoutService.checkout(c));
        verifyNoInteractions(paypalService);
        verifyNoInteractions(ordersRepository);   // ni siquiera libera pendientes: primero va el lock
    }

    @Test
    @DisplayName("When the cart is empty, it throws EmptyCartException and never calls PayPal")
    void checkout_emptyCart_throwsEmptyCart() {
        // Arrange
        Customer c = fakeCustomer();
        Cart cart = fakeCart(cart1 -> cart1.setCustomer(c));

        when(cartRepository.findByCustomerForUpdate(c))
                .thenReturn(Optional.of(cart));

        // Act + Assert
        assertThrows(EmptyCartException.class,
                () -> checkoutService.checkout(c));
        verifyNoInteractions(paypalService);
        verifyNoInteractions(ordersRepository);
    }

    @Test
    @DisplayName("When a product in the cart is inactive, it throws ProductNotFoundException")
    void checkout_inactiveProduct_throwsProductNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        Product inactivo = fakeProduct(p -> {
            p.setId(1L);
            p.setActive(false);
        });
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, inactivo, 2);

        when(ordersRepository.findByBuyerAndStatus(c, OrderStatus.PENDING_PAYMENT))
                .thenReturn(List.of());
        when(cartRepository.findByCustomerForUpdate(c))
                .thenReturn(Optional.of(cart));
        when(ordersRepository.save(any(Orders.class)))
                .thenAnswer(inv -> inv.getArgument(0)); //Osea cada que alguien llame a un save con un Orders regresale con lo que le llamaron

        // Act + Assert
        assertThrows(ProductNotFoundException.class,
                () -> checkoutService.checkout(c));
        verifyNoInteractions(virtualCodeRepository);
        verifyNoInteractions(paypalService);
    }

    @Test
    @DisplayName("When there are fewer available codes than requested, it throws QuantityExceededException")
    void checkout_notEnoughCodes_throwsQuantityExceeded() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);// pide 2

        VirtualCode c1 = fakeVirtualCode(p, "AAA");// AVAILABLE por default

        when(ordersRepository.findByBuyerAndStatus(c, OrderStatus.PENDING_PAYMENT))
                .thenReturn(List.of());
        when(cartRepository.findByCustomerForUpdate(c))
                .thenReturn(Optional.of(cart));
        when(ordersRepository.save(any(Orders.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(virtualCodeRepository.findByProductIdAndStatusForUpdate(
                1L, CodeStatus.AVAILABLE, PageRequest.of(0, 2)))
                .thenReturn(List.of(c1));// solo hay 1

        // Act + Assert
        assertThrows(QuantityExceededException.class,
                () -> checkoutService.checkout(c));
        verifyNoInteractions(paypalService);
    }

    @Test
    @DisplayName("When checking out, the codes are RESERVED, linked to the order and given an expiration date")
    void checkout_valid_reservesCodes() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);                                   // pide 2

        VirtualCode code1 = fakeVirtualCode(p, "AAA");             // AVAILABLE por default
        VirtualCode code2 = fakeVirtualCode(p, "BBB");

        AtomicReference<Orders> savedOrder = new AtomicReference<>(); //Caja que guarda el objeto

        when(ordersRepository.findByBuyerAndStatus(c, OrderStatus.PENDING_PAYMENT))
                .thenReturn(List.of());
        when(cartRepository.findByCustomerForUpdate(c))
                .thenReturn(Optional.of(cart));
        when(ordersRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders o = inv.getArgument(0);
            o.setId(10L);
            o.setStatus(OrderStatus.PENDING_PAYMENT);   // por si la entidad no lo trae por default
            savedOrder.set(o);
            return o;
        });
        when(virtualCodeRepository.findByProductIdAndStatusForUpdate(
                1L, CodeStatus.AVAILABLE, PageRequest.of(0, 2)))
                .thenReturn(List.of(code1, code2));
        when(paypalService.createOrder(any(BigDecimal.class), eq("MXN"), anyString()))
                .thenReturn("PAYPAL-1");
        when(ordersRepository.findById(10L))
                .thenAnswer(inv -> Optional.of(savedOrder.get()));

        LocalDateTime antes = LocalDateTime.now();

        // Act
        checkoutService.checkout(c);

        // Assert
        for (VirtualCode code : List.of(code1, code2)) {
            assertEquals(CodeStatus.RESERVED, code.getStatus());
            assertSame(savedOrder.get(), code.getOrders());
            assertNotNull(code.getExpiresAt());
            assertFalse(code.getExpiresAt().isBefore(antes.plusMinutes(15)));   // reservationMinutes = 15
        }
    }
    //Dia 2
    @Test
    @DisplayName("When checking out, the total and unit prices come from the product sale price, not the cart")
    void checkout_valid_totalUsesProductSalePrice() {
        // Arrange
        Customer c = fakeCustomer();
        Product p1 = fakeProduct(p -> { p.setId(1L); p.setSalePrice(new BigDecimal("100.00")); });
        Product p2 = fakeProduct(p -> { p.setId(2L); p.setSalePrice(new BigDecimal("50.50")); });
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p1, 2);
        addCartItem(cart, p2, 3);

        AtomicReference<Orders> saved = stubCheckout(c, cart);
        stubAvailableCodes(p1, 2);
        stubAvailableCodes(p2, 3);
        stubPaypalCreate();

        // Act
        checkoutService.checkout(c);

        // Assert
        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paypalService).createOrder(amount.capture(), eq("MXN"), anyString());
        BigDecimal esperado = new BigDecimal("351.50");   // 100*2 + 50.50*3
        assertEquals(0, esperado.compareTo(amount.getValue()));
        assertEquals(0, esperado.compareTo(saved.get().getTotalAmount()));

        List<OrderItem> items = saved.get().getItems();
        assertEquals(0, new BigDecimal("100.00").compareTo(items.get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("50.50").compareTo(items.get(1).getUnitPrice()));
    }

    @Test
    @DisplayName("When checking out, the order items are created with product, quantity and unit price")
    void checkout_valid_createsOrderItems() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);

        AtomicReference<Orders> saved = stubCheckout(c, cart);
        stubAvailableCodes(p, 2);
        stubPaypalCreate();

        // Act
        checkoutService.checkout(c);

        // Assert
        assertEquals(1, saved.get().getItems().size());
        OrderItem item = saved.get().getItems().get(0);
        assertSame(p, item.getProduct());
        assertEquals(2, item.getQuantity());
        assertEquals(p.getSalePrice(), item.getUnitPrice());
        assertSame(saved.get(), item.getOrders());
    }

    @Test
    @DisplayName("When the cart has several products, codes are locked in ascending productId order")
    void checkout_multipleProducts_locksCodesInProductIdOrder() {
        // Arrange
        Customer c = fakeCustomer();
        Product p5 = fakeProduct(p -> p.setId(5L));
        Product p2 = fakeProduct(p -> p.setId(2L));
        Product p9 = fakeProduct(p -> p.setId(9L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p5, 1);
        addCartItem(cart, p2, 1);
        addCartItem(cart, p9, 1);

        stubCheckout(c, cart);
        stubAvailableCodes(p5, 1);
        stubAvailableCodes(p2, 1);
        stubAvailableCodes(p9, 1);
        stubPaypalCreate();

        // Act
        checkoutService.checkout(c);

        // Assert
        InOrder inOrder = inOrder(virtualCodeRepository);
        inOrder.verify(virtualCodeRepository)
                .findByProductIdAndStatusForUpdate(eq(2L), eq(CodeStatus.AVAILABLE), any(Pageable.class));
        inOrder.verify(virtualCodeRepository)
                .findByProductIdAndStatusForUpdate(eq(5L), eq(CodeStatus.AVAILABLE), any(Pageable.class));
        inOrder.verify(virtualCodeRepository)
                .findByProductIdAndStatusForUpdate(eq(9L), eq(CodeStatus.AVAILABLE), any(Pageable.class));
    }
    //Dia 3
    @Test
    @DisplayName("When the customer has older PENDING_PAYMENT orders, they are released before creating the new one")
    void checkout_previousPendingOrders_areReleased() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 1);

        Orders vieja = fakeOrder(o -> {
            o.setId(99L);
            o.setBuyer(c);
            o.setStatus(OrderStatus.PENDING_PAYMENT);
        });
        VirtualCode codigoViejo = fakeVirtualCode(p, "OLD", vc -> {
            vc.setStatus(CodeStatus.RESERVED);
            vc.setOrders(vieja);
            vc.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        });

        stubCheckout(c, cart, List.of(vieja));
        when(ordersRepository.findById(99L)).thenReturn(Optional.of(vieja));
        when(virtualCodeRepository.findByOrdersId(99L)).thenReturn(List.of(codigoViejo));
        stubAvailableCodes(p, 1);
        stubPaypalCreate();

        // Act
        checkoutService.checkout(c);

        // Assert
        assertEquals(OrderStatus.CANCELLED, vieja.getStatus());
        assertEquals(CodeStatus.AVAILABLE, codigoViejo.getStatus());
        assertNull(codigoViejo.getOrders());
        assertNull(codigoViejo.getExpiresAt());

        InOrder inOrder = inOrder(ordersRepository);
        inOrder.verify(ordersRepository).findById(99L);           // primero libera la vieja
        inOrder.verify(ordersRepository).save(any(Orders.class)); // luego crea la nueva
    }

    @Test
    @DisplayName("When checking out, the cart is locked before releasing the older pending orders (double click protection)")
    void checkout_locksCartBeforeReleasingPendingOrders() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 1);

        stubCheckout(c, cart);
        stubAvailableCodes(p, 1);
        stubPaypalCreate();

        // Act
        checkoutService.checkout(c);

        // Assert
        InOrder inOrder = inOrder(cartRepository, ordersRepository);
        inOrder.verify(cartRepository).findByCustomerForUpdate(c);                          // 1o: lock del carrito
        inOrder.verify(ordersRepository).findByBuyerAndStatus(c, OrderStatus.PENDING_PAYMENT); // 2o: libera pendientes
    }

    @Test
    @DisplayName("When checkout succeeds, it saves the PayPal order id in the order and returns it")
    void checkout_valid_storesAndReturnsPaypalOrderId() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 1);

        AtomicReference<Orders> saved = stubCheckout(c, cart);
        stubAvailableCodes(p, 1);
        stubPaypalCreate();

        // Act
        String result = checkoutService.checkout(c);

        // Assert
        assertEquals("PAYPAL-1", result);
        assertEquals("PAYPAL-1", saved.get().getPaypalOrderId());
    }

    @Test
    @DisplayName("When another request cancels the order while PayPal creates it, the PayPal id is not linked and it throws OrderNotPayableException")
    void checkout_orderCancelledWhileCreatingPaypalOrder_throwsOrderNotPayable() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 1);

        AtomicReference<Orders> saved = stubCheckout(c, cart);
        stubAvailableCodes(p, 1);
        when(paypalService.createOrder(any(BigDecimal.class), eq("MXN"), anyString())).thenAnswer(inv -> {
            saved.get().setStatus(OrderStatus.CANCELLED);   // otra petición (doble clic) lo canceló en medio
            return "PAYPAL-1";
        });

        // Act + Assert
        assertThrows(OrderNotPayableException.class, () -> checkoutService.checkout(c));
        assertNull(saved.get().getPaypalOrderId());
    }

    @Test
    @DisplayName("When PayPal fails creating the order, the codes are released and the exception is rethrown")
    void checkout_paypalFails_releasesOrderAndRethrows() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);

        AtomicReference<Orders> saved = stubCheckout(c, cart);
        List<VirtualCode> codes = stubAvailableCodes(p, 2);
        IllegalStateException error = new IllegalStateException("PayPal down");
        when(paypalService.createOrder(any(BigDecimal.class), eq("MXN"), anyString())).thenThrow(error);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(codes);   // para freeReservedCodes

        // Act + Assert
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> checkoutService.checkout(c));
        assertSame(error, thrown);
        assertEquals(OrderStatus.CANCELLED, saved.get().getStatus());
        for (VirtualCode code : codes) {
            assertEquals(CodeStatus.AVAILABLE, code.getStatus());
            assertNull(code.getOrders());
            assertNull(code.getExpiresAt());
        }
    }

    //  confirmPayment

    @Test
    @DisplayName("When the PayPal order does not belong to the customer or does not exist, it throws OrderNotFoundException")
    void confirmPayment_notFound_throwsOrderNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        when(ordersRepository.findByPaypalOrderIdAndBuyer("PP-X", c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(OrderNotFoundException.class, () -> checkoutService.confirmPayment("PP-X", c));
        verifyNoInteractions(paypalService);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "REFUNDED"}) //Sirve para probar un test con diferentes campos en este caso el Enum
    @DisplayName("When the order is already PAID or REFUNDED, it returns it without calling PayPal (idempotent)")
    void confirmPayment_alreadyProcessed_returnsWithoutCapturing(OrderStatus status) {
        // Arrange
        Customer c = fakeCustomer();
        Orders order = fakeOrder(o -> { o.setId(10L); o.setBuyer(c); o.setStatus(status); });
        when(ordersRepository.findByPaypalOrderIdAndBuyer("PP-1", c)).thenReturn(Optional.of(order));

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertSame(order, result);
        verifyNoInteractions(paypalService);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "PAID", "REFUNDED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("When the order is in a non payable status, it throws OrderNotPayableException")
    void confirmPayment_notPayableStatus_throwsOrderNotPayable(OrderStatus status) {
        // Arrange
        Customer c = fakeCustomer();
        Orders order = fakeOrder(o -> { o.setId(10L); o.setBuyer(c); o.setStatus(status); });
        when(ordersRepository.findByPaypalOrderIdAndBuyer("PP-1", c)).thenReturn(Optional.of(order));

        // Act + Assert
        assertThrows(OrderNotPayableException.class, () -> checkoutService.confirmPayment("PP-1", c));
        verifyNoInteractions(paypalService);
    }

    @Test
    @DisplayName("When the reservation expired (missing codes), it releases the order, throws ReservationExpiredException and does not capture")
    void confirmPayment_reservationMissingCodes_releasesAndThrows() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 2);                        // espera 2 códigos
        when(virtualCodeRepository.findByOrdersId(10L))
                .thenReturn(List.of(pe.codes().get(0)));               // solo queda 1

        // Act + Assert
        assertThrows(ReservationExpiredException.class, () -> checkoutService.confirmPayment("PP-1", c));
        verify(paypalService, never()).captureOrder(any(), any());
        assertEquals(OrderStatus.CANCELLED, pe.order().getStatus());
        assertEquals(CodeStatus.AVAILABLE, pe.codes().get(0).getStatus());   // se liberó
    }

    @Test
    @DisplayName("When some reserved codes are no longer RESERVED, it throws ReservationExpiredException")
    void confirmPayment_reservationCodeNotReserved_throwsReservationExpired() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        pe.codes().get(0).setStatus(CodeStatus.AVAILABLE);             // ya no está reservado
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());

        // Act + Assert
        assertThrows(ReservationExpiredException.class, () -> checkoutService.confirmPayment("PP-1", c));
        verify(paypalService, never()).captureOrder(any(), any());
    }

    @Test
    @DisplayName("When PayPal says ORDER_ALREADY_CAPTURED, it waits and returns the order once it is no longer pending")
    void confirmPayment_alreadyCaptured_returnsOutcomeOfOtherRequest() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        Orders pagada = fakeOrder(o -> { o.setId(10L); o.setBuyer(c); o.setStatus(OrderStatus.PAID); });

        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenThrow(unprocessable("{\"details\":[{\"issue\":\"ORDER_ALREADY_CAPTURED\"}]}"));
        // 1a llamada: hasValidReservation (pendiente). 2a: waitForOutcome ya la ve pagada.
        when(ordersRepository.findById(10L))
                .thenReturn(Optional.of(pe.order()), Optional.of(pagada));

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.PAID, result.getStatus());
    }

    @Test
    @DisplayName("When PayPal says ORDER_ALREADY_CAPTURED but the order stays pending, it throws PaymentProcessingException")
    void confirmPayment_alreadyCapturedNeverFinishes_throwsPaymentProcessing() {
        // Arrange (tarda ~2s por el Thread.sleep: 5 x 400ms)
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenThrow(unprocessable("ORDER_ALREADY_CAPTURED"));

        // Act + Assert
        assertThrows(PaymentProcessingException.class, () -> checkoutService.confirmPayment("PP-1", c));
    }

    @Test
    @DisplayName("When PayPal rejects the capture with another 422, the exception is rethrown")
    void confirmPayment_otherUnprocessable_rethrows() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        HttpClientErrorException error = unprocessable("INSTRUMENT_DECLINED");
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID)).thenThrow(error);

        // Act + Assert
        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class,
                () -> checkoutService.confirmPayment("PP-1", c));
        assertSame(error, thrown);
    }

    @Test
    @DisplayName("When PayPal rejects the capture with another 4xx (definitive rejection), it is rethrown as is and nothing is charged")
    void confirmPayment_otherClientError_rethrowsAsIs() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        HttpClientErrorException error = httpClientError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID)).thenThrow(error);

        // Act + Assert
        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class,
                () -> checkoutService.confirmPayment("PP-1", c));
        assertSame(error, thrown);                                        // no se envuelve
        assertNull(pe.order().getPaypalCaptureId());
        verify(paypalService, never()).refundCapture(any(), any());
    }

    //  confirmPayment: fallo ambiguo en la captura (timeout / 5xx)

    @Test
    @DisplayName("When the capture times out (ambiguous), it throws PaymentProcessingException and leaves the order PENDING with its codes RESERVED")
    void confirmPayment_captureTimeout_leavesOrderPending() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 2);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenThrow(new ResourceAccessException("Read timed out"));

        // Act + Assert
        assertThrows(PaymentProcessingException.class, () -> checkoutService.confirmPayment("PP-1", c));

        assertEquals(OrderStatus.PENDING_PAYMENT, pe.order().getStatus());   // NO se cancela
        for (VirtualCode code : pe.codes()) {
            assertEquals(CodeStatus.RESERVED, code.getStatus());             // NO se liberan
            assertSame(pe.order(), code.getOrders());
        }
        verify(paypalService, never()).refundCapture(any(), any());
    }

    @Test
    @DisplayName("When PayPal answers 5xx on the capture (ambiguous), it throws PaymentProcessingException and leaves the order PENDING")
    void confirmPayment_capture5xx_leavesOrderPending() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        // Act + Assert
        assertThrows(PaymentProcessingException.class, () -> checkoutService.confirmPayment("PP-1", c));
        assertEquals(OrderStatus.PENDING_PAYMENT, pe.order().getStatus());
        assertEquals(CodeStatus.RESERVED, pe.codes().get(0).getStatus());
    }

    @Test
    @DisplayName("When the first capture is ambiguous and the client retries, it reuses the same PayPal request id and completes the payment")
    void confirmPayment_retryAfterAmbiguousFailure_reusesRequestIdAndCompletes() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 1);
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenThrow(new ResourceAccessException("Read timed out"))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe)));

        // Act
        assertThrows(PaymentProcessingException.class, () -> checkoutService.confirmPayment("PP-1", c));
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.PAID, result.getStatus());
        assertEquals(CodeStatus.SOLD, pe.codes().get(0).getStatus());
        verify(paypalService, times(2)).captureOrder("PP-1", CAPTURE_REQUEST_ID);   // mismo requestId => PayPal no cobra doble
    }

    @Test
    @DisplayName("When capturing, it sends a fixed request id per order so PayPal can deduplicate retries")
    void confirmPayment_usesIdempotentRequestIdForCapture() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 1);
        stubSuccessfulPayment(c, p, 1, cart);

        // Act
        checkoutService.confirmPayment("PP-1", c);

        // Assert
        verify(paypalService).captureOrder("PP-1", "capture-order-10");
    }

    //  confirmPayment: validación de la captura

    @Test
    @DisplayName("When the capture response status is not COMPLETED, it throws PaymentNotCompletedException")
    void confirmPayment_orderStatusNotCompleted_throwsPaymentNotCompleted() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("PENDING", "CAP-1", "COMPLETED", "210.00"));

        // Act + Assert
        assertThrows(PaymentNotCompletedException.class, () -> checkoutService.confirmPayment("PP-1", c));
    }

    @Test
    @DisplayName("When the capture has no id, it throws PaymentNotCompletedException")
    void confirmPayment_missingCaptureId_throwsPaymentNotCompleted() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", null, "COMPLETED", "210.00"));

        // Act + Assert
        assertThrows(PaymentNotCompletedException.class, () -> checkoutService.confirmPayment("PP-1", c));
    }

    @Test
    @DisplayName("When the capture status is not COMPLETED, it throws PaymentNotCompletedException")
    void confirmPayment_captureNotCompleted_throwsPaymentNotCompleted() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "PENDING", "210.00"));

        // Act + Assert
        assertThrows(PaymentNotCompletedException.class, () -> checkoutService.confirmPayment("PP-1", c));
    }

    @Test
    @DisplayName("When the payment is valid, the order is PAID, the codes are SOLD and the capture id is saved")
    void confirmPayment_valid_marksOrderPaidAndCodesSold() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);
        Pending pe = stubSuccessfulPayment(c, p, 2, cart);

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.PAID, result.getStatus());
        assertEquals("CAP-1", result.getPaypalCaptureId());
        for (VirtualCode code : pe.codes()) assertEquals(CodeStatus.SOLD, code.getStatus());
    }
    ///jiji
    @Test
    @DisplayName("When the payment is valid, the codes get soldAt and lose their expiration date")
    void confirmPayment_valid_setsSoldAtAndClearsExpiration() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);
        Pending pe = stubSuccessfulPayment(c, p, 2, cart);

        // Act
        checkoutService.confirmPayment("PP-1", c);

        // Assert
        for (VirtualCode code : pe.codes()) {
            assertNotNull(code.getSoldAt());
            assertNull(code.getExpiresAt());
        }
    }

    @Test
    @DisplayName("When the payment is valid, the customer cart is emptied")
    void confirmPayment_valid_clearsCart() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);
        stubSuccessfulPayment(c, p, 2, cart);

        // Act
        checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    @DisplayName("When the captured amount differs from the order total, the order is FAILED, codes are freed and a refund is issued")
    void confirmPayment_amountMismatch_failsFreesCodesAndRefunds() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);                        // total 210.00
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", "1.00"));   // monto incorrecto
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("COMPLETED");

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals("CAP-1", result.getPaypalCaptureId());            // se guardó aunque falló
        assertEquals(OrderStatus.REFUNDED, result.getStatus());        // FAILED -> reembolsado
        assertEquals(CodeStatus.AVAILABLE, pe.codes().get(0).getStatus());
        assertNull(pe.codes().get(0).getOrders());
        verify(paypalService).refundCapture("CAP-1", "refund-order-10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR"})
    @DisplayName("When the captured currency is not MXN (even with the right amount), the order is FAILED and refunded")
    void confirmPayment_wrongCurrency_failsAndRefunds(String currency) {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe), currency));
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("COMPLETED");

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        assertEquals(CodeStatus.AVAILABLE, pe.codes().get(0).getStatus());
        verify(paypalService).refundCapture("CAP-1", "refund-order-10");
        verifyNoInteractions(cartRepository);
    }

    @Test
    @DisplayName("When the capture has no currency code, the order is FAILED and refunded")
    void confirmPayment_missingCurrency_failsAndRefunds() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe), null));
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("COMPLETED");

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.REFUNDED, result.getStatus());
    }

    @Test
    @DisplayName("When the reservation is lost after the charge, the order is FAILED and refunded")
    void confirmPayment_reservationLostAfterCharge_failsAndRefunds() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        // 1a (pre-chequeo): ok. 2a (después del cobro): se perdió. 3a: freeReservedCodes.
        when(virtualCodeRepository.findByOrdersId(10L))
                .thenReturn(pe.codes(), List.of(), pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe)));
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("COMPLETED");

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        assertEquals(CodeStatus.AVAILABLE, pe.codes().get(0).getStatus());
        verify(paypalService).refundCapture("CAP-1", "refund-order-10");
    }

    @Test
    @DisplayName("When the amount is correct, the cart is not touched if the order failed")
    void confirmPayment_failed_doesNotClearCart() {
        // Arrange (monto correcto, pero la reserva se pierde después del cobro)
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L))
                .thenReturn(pe.codes(), List.of(), pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe)));
        when(paypalService.refundCapture(anyString(), anyString())).thenReturn("COMPLETED");

        // Act
        checkoutService.confirmPayment("PP-1", c);

        // Assert
        verifyNoInteractions(cartRepository);
    }

    @Test
    @DisplayName("When the order FAILED and the refund also fails, the order stays FAILED")
    void confirmPayment_failedAndRefundFails_staysFailed() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", "1.00"));   // monto incorrecto
        when(paypalService.refundCapture("CAP-1", "refund-order-10"))
                .thenThrow(new RuntimeException("PayPal caído"));

        // Act
        Orders result = assertDoesNotThrow(() -> checkoutService.confirmPayment("PP-1", c));

        // Assert
        assertEquals(OrderStatus.FAILED, result.getStatus());
        assertEquals("CAP-1", result.getPaypalCaptureId());
    }

    @Test
    @DisplayName("When another request already settled the order while capturing, it does not touch codes, cart or refund again")
    void confirmPayment_settledByAnotherRequestDuringCapture_isNotProcessedTwice() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        JsonNode ok = captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe));
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID)).thenAnswer(inv -> {
            pe.order().setStatus(OrderStatus.PAID);   // webhook / otra petición lo resolvió mientras tanto
            return ok;
        });

        // Act
        Orders result = checkoutService.confirmPayment("PP-1", c);

        // Assert
        assertEquals(OrderStatus.PAID, result.getStatus());
        assertEquals(CodeStatus.RESERVED, pe.codes().get(0).getStatus());   // esta petición no los tocó
        verifyNoInteractions(cartRepository);
        verify(paypalService, never()).refundCapture(any(), any());
    }

    //  expireOrReconcile (lo usa el job)

    @Test
    @DisplayName("When reconciling an order that does not exist, it throws OrderNotFoundException")
    void expireOrReconcile_notFound_throwsOrderNotFound() {
        // Arrange
        when(ordersRepository.findById(10L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(OrderNotFoundException.class, () -> checkoutService.expireOrReconcile(10L));
        verifyNoInteractions(paypalService);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("When reconciling an order that is not pending, it does nothing and does not call PayPal")
    void expireOrReconcile_notPending_doesNothing(OrderStatus status) {
        // Arrange
        Orders order = stubOrderById(status, null);

        // Act
        checkoutService.expireOrReconcile(10L);

        // Assert
        assertEquals(status, order.getStatus());
        verifyNoInteractions(paypalService);
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("When the pending order has no PayPal order id, it is cancelled without calling PayPal")
    void expireOrReconcile_noPaypalOrderId_releasesWithoutCallingPaypal() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrderById(c, p, 2);
        pe.order().setPaypalOrderId(null);                              // nunca se creó la orden en PayPal
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());

        // Act
        checkoutService.expireOrReconcile(10L);

        // Assert
        assertEquals(OrderStatus.CANCELLED, pe.order().getStatus());
        for (VirtualCode code : pe.codes()) assertEquals(CodeStatus.AVAILABLE, code.getStatus());
        verifyNoInteractions(paypalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "APPROVED", "VOIDED"})
    @DisplayName("When PayPal confirms the order was not charged, the pending order is cancelled and its codes freed")
    void expireOrReconcile_paypalNotCompleted_releases(String paypalStatus) {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrderById(c, p, 2);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.getOrder("PP-1")).thenReturn(json("{\"status\":\"" + paypalStatus + "\"}"));

        // Act
        checkoutService.expireOrReconcile(10L);

        // Assert
        assertEquals(OrderStatus.CANCELLED, pe.order().getStatus());
        for (VirtualCode code : pe.codes()) {
            assertEquals(CodeStatus.AVAILABLE, code.getStatus());
            assertNull(code.getOrders());
        }
        verify(paypalService, never()).captureOrder(any(), any());      // nunca captura una orden vencida
    }

    @Test
    @DisplayName("When PayPal cannot be reached, the pending order is NOT cancelled (it will be retried later)")
    void expireOrReconcile_paypalUnreachable_doesNotCancel() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrderById(c, p, 2);
        when(paypalService.getOrder("PP-1")).thenThrow(new ResourceAccessException("timeout"));

        // Act
        assertDoesNotThrow(() -> checkoutService.expireOrReconcile(10L));

        // Assert
        assertEquals(OrderStatus.PENDING_PAYMENT, pe.order().getStatus());
        for (VirtualCode code : pe.codes()) assertEquals(CodeStatus.RESERVED, code.getStatus());
        verifyNoInteractions(virtualCodeRepository);                    // no liberó nada
    }

    @Test
    @DisplayName("When PayPal already charged a still-pending order, it settles it: PAID, codes SOLD and cart emptied")
    void expireOrReconcile_paypalCompleted_settlesAsPaid() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Cart cart = fakeCart(car -> car.setCustomer(c));
        addCartItem(cart, p, 2);
        Pending pe = stubPendingOrderById(c, p, 2);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(paypalService.getOrder("PP-1"))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe)));

        // Act
        checkoutService.expireOrReconcile(10L);

        // Assert
        assertEquals(OrderStatus.PAID, pe.order().getStatus());
        assertEquals("CAP-1", pe.order().getPaypalCaptureId());
        for (VirtualCode code : pe.codes()) {
            assertEquals(CodeStatus.SOLD, code.getStatus());
            assertNotNull(code.getSoldAt());
            assertNull(code.getExpiresAt());
        }
        assertTrue(cart.getItems().isEmpty());
        verify(paypalService, never()).refundCapture(any(), any());
    }

    @Test
    @DisplayName("When PayPal charged a still-pending order with a wrong amount, it is FAILED and refunded")
    void expireOrReconcile_paypalCompletedWrongAmount_failsAndRefunds() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrderById(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(paypalService.getOrder("PP-1"))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", "1.00"));
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("COMPLETED");

        // Act
        checkoutService.expireOrReconcile(10L);

        // Assert
        assertEquals(OrderStatus.REFUNDED, pe.order().getStatus());
        assertEquals(CodeStatus.AVAILABLE, pe.codes().get(0).getStatus());
        verify(paypalService).refundCapture("CAP-1", "refund-order-10");
    }

    @Test
    @DisplayName("When PayPal charged a still-pending order but its reservation is gone, it is FAILED and refunded")
    void expireOrReconcile_paypalCompletedReservationLost_failsAndRefunds() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrderById(c, p, 1);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(List.of());   // ya no hay códigos ligados
        when(paypalService.getOrder("PP-1"))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe)));
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("COMPLETED");

        // Act
        checkoutService.expireOrReconcile(10L);

        // Assert
        assertEquals(OrderStatus.REFUNDED, pe.order().getStatus());
        verify(paypalService).refundCapture("CAP-1", "refund-order-10");
        verifyNoInteractions(cartRepository);
    }

    //  cancelOrder

    @Test
    @DisplayName("When cancelling an order that does not exist for the customer, it throws OrderNotFoundException")
    void cancelOrder_notFound_throwsOrderNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        when(ordersRepository.findByPaypalOrderIdAndBuyer("PP-X", c)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(OrderNotFoundException.class, () -> checkoutService.cancelOrder("PP-X", c));
        verifyNoInteractions(virtualCodeRepository);
    }
    /// jiji2
    @Test
    @DisplayName("When cancelling a pending order, it becomes CANCELLED and its reserved codes become AVAILABLE")
    void cancelOrder_pending_cancelsAndFreesCodes() {
        // Arrange
        Customer c = fakeCustomer();
        Product p = fakeProduct(pr -> pr.setId(1L));
        Pending pe = stubPendingOrder(c, p, 2);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());

        // Act
        checkoutService.cancelOrder("PP-1", c);

        // Assert
        assertEquals(OrderStatus.CANCELLED, pe.order().getStatus());
        for (VirtualCode code : pe.codes()) {
            assertEquals(CodeStatus.AVAILABLE, code.getStatus());
            assertNull(code.getOrders());
            assertNull(code.getExpiresAt());
        }
    }

    //  releaseOrder

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("When releasing an order that is not pending, it does not change the order or its codes")
    void releaseOrder_notPending_doesNothing(OrderStatus status) {
        // Arrange
        Orders order = stubOrderById(status, null);

        // Act
        checkoutService.releaseOrder(10L);

        // Assert
        assertEquals(status, order.getStatus());
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("When releasing a pending order, only RESERVED codes are freed")
    void releaseOrder_pending_onlyFreesReservedCodes() {
        // Arrange
        Product p = fakeProduct(pr -> pr.setId(1L));
        Orders order = stubOrderById(OrderStatus.PENDING_PAYMENT, null);
        VirtualCode reservado = fakeVirtualCode(p, "RES", vc -> {
            vc.setStatus(CodeStatus.RESERVED);
            vc.setOrders(order);
        });
        VirtualCode vendido = fakeVirtualCode(p, "SOLD", vc -> {
            vc.setStatus(CodeStatus.SOLD);
            vc.setOrders(order);
        });
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(List.of(reservado, vendido));

        // Act
        checkoutService.releaseOrder(10L);

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CodeStatus.AVAILABLE, reservado.getStatus());
        assertNull(reservado.getOrders());
        assertEquals(CodeStatus.SOLD, vendido.getStatus());            // intacto
        assertSame(order, vendido.getOrders());
    }

    @Test
    @DisplayName("When releasing an order that does not exist, it throws OrderNotFoundException")
    void releaseOrder_notFound_throwsOrderNotFound() {
        // Arrange
        when(ordersRepository.findById(10L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(OrderNotFoundException.class, () -> checkoutService.releaseOrder(10L));
    }

    //  refundOrder

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("When refunding an order that is not FAILED, it does nothing")
    void refundOrder_notFailed_doesNothing(OrderStatus status) {
        // Arrange
        Orders order = stubOrderById(status, "CAP-1");

        // Act
        checkoutService.refundOrder(10L);

        // Assert
        assertEquals(status, order.getStatus());
        verifyNoInteractions(paypalService);
    }

    @Test
    @DisplayName("When refunding a FAILED order without capture id, it does nothing")
    void refundOrder_noCaptureId_doesNothing() {
        // Arrange
        Orders order = stubOrderById(OrderStatus.FAILED, null);

        // Act
        checkoutService.refundOrder(10L);

        // Assert
        assertEquals(OrderStatus.FAILED, order.getStatus());
        verifyNoInteractions(paypalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "PENDING"})
    @DisplayName("When PayPal refunds with COMPLETED or PENDING, the order becomes REFUNDED")
    void refundOrder_successStatus_marksRefunded(String paypalStatus) {
        // Arrange
        Orders order = stubOrderById(OrderStatus.FAILED, "CAP-1");
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn(paypalStatus);

        // Act
        checkoutService.refundOrder(10L);

        // Assert
        assertEquals(OrderStatus.REFUNDED, order.getStatus());
    }

    @Test
    @DisplayName("When PayPal returns another refund status, the order stays FAILED")
    void refundOrder_otherStatus_staysFailed() {
        // Arrange
        Orders order = stubOrderById(OrderStatus.FAILED, "CAP-1");
        when(paypalService.refundCapture("CAP-1", "refund-order-10")).thenReturn("DECLINED");

        // Act
        checkoutService.refundOrder(10L);

        // Assert
        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    @DisplayName("When PayPal says CAPTURE_FULLY_REFUNDED, the order becomes REFUNDED")
    void refundOrder_alreadyRefunded_marksRefunded() {
        // Arrange
        Orders order = stubOrderById(OrderStatus.FAILED, "CAP-1");
        when(paypalService.refundCapture("CAP-1", "refund-order-10"))
                .thenThrow(unprocessable("{\"details\":[{\"issue\":\"CAPTURE_FULLY_REFUNDED\"}]}"));

        // Act
        checkoutService.refundOrder(10L);

        // Assert
        assertEquals(OrderStatus.REFUNDED, order.getStatus());
    }

    @Test
    @DisplayName("When PayPal rejects the refund with another 422, the order stays FAILED")
    void refundOrder_otherUnprocessable_staysFailed() {
        // Arrange
        Orders order = stubOrderById(OrderStatus.FAILED, "CAP-1");
        when(paypalService.refundCapture("CAP-1", "refund-order-10"))
                .thenThrow(unprocessable("REFUND_NOT_ALLOWED"));

        // Act
        assertDoesNotThrow(() -> checkoutService.refundOrder(10L));

        // Assert
        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    @DisplayName("When the refund throws a RuntimeException, it is swallowed and the order stays FAILED for retry")
    void refundOrder_runtimeException_staysFailed() {
        // Arrange
        Orders order = stubOrderById(OrderStatus.FAILED, "CAP-1");
        when(paypalService.refundCapture("CAP-1", "refund-order-10"))
                .thenThrow(new RuntimeException("timeout"));

        // Act
        assertDoesNotThrow(() -> checkoutService.refundOrder(10L));

        // Assert
        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    @DisplayName("When refunding, it uses a fixed request id per order to avoid duplicate refunds")
    void refundOrder_usesIdempotentRequestId() {
        // Arrange
        stubOrderById(OrderStatus.FAILED, "CAP-1");
        when(paypalService.refundCapture(anyString(), anyString())).thenReturn("COMPLETED");

        // Act
        checkoutService.refundOrder(10L);

        // Assert
        verify(paypalService).refundCapture("CAP-1", "refund-order-10");
    }

    //  helpers

    /** Si ya lo agregaste a OrderFakes, borra este (el de la clase tiene prioridad sobre el import estático). */
    private static OrderItem addOrderItem(Orders order, Product product, int quantity, BigDecimal unitPrice) {
        OrderItem item = new OrderItem();
        item.setOrders(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        order.getItems().add(item);
        return item;
    }

    private AtomicReference<Orders> stubCheckout(Customer c, Cart cart) {
        return stubCheckout(c, cart, List.of());
    }

    /** Stubea el camino común de checkout. Devuelve una "caja" con la orden que crea el service. */
    private AtomicReference<Orders> stubCheckout(Customer c, Cart cart, List<Orders> pending) {
        AtomicReference<Orders> saved = new AtomicReference<>();
        when(ordersRepository.findByBuyerAndStatus(c, OrderStatus.PENDING_PAYMENT)).thenReturn(pending); //1
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));//2
        when(ordersRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders o = inv.getArgument(0);
            o.setId(10L);
            o.setStatus(OrderStatus.PENDING_PAYMENT);   // por si la entidad no lo trae por default
            saved.set(o);
            return o;
        });
        //3
        when(ordersRepository.findById(10L)).thenAnswer(inv -> Optional.of(saved.get()));
        return saved;
    }

    private void stubPaypalCreate() {
        when(paypalService.createOrder(any(BigDecimal.class), eq("MXN"), anyString()))
                .thenReturn("PAYPAL-1");
    }

    private List<VirtualCode> stubAvailableCodes(Product p, int qty) {
        List<VirtualCode> codes = new ArrayList<>();
        for (int i = 0; i < qty; i++) codes.add(fakeVirtualCode(p, "CODE-" + p.getId() + "-" + i));
        when(virtualCodeRepository.findByProductIdAndStatusForUpdate(
                p.getId(), CodeStatus.AVAILABLE, PageRequest.of(0, qty))).thenReturn(codes);
        return codes;
    }

    private List<VirtualCode> reservedCodes(Product p, Orders order, int n) {
        List<VirtualCode> codes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            codes.add(fakeVirtualCode(p, "RES-" + i, vc -> {
                vc.setStatus(CodeStatus.RESERVED);
                vc.setOrders(order);
                vc.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            }));
        }
        return codes;
    }

    private record Pending(Orders order, List<VirtualCode> codes) {}

    /**
     * Orden PENDING_PAYMENT (id 10, paypalOrderId "PP-1") con item y códigos RESERVED.
     * Solo stubea findById: es lo que usan expireOrReconcile / releaseOrder (el job).
     */
    private Pending stubPendingOrderById(Customer c, Product p, int qty) {
        Orders order = fakeOrder(o -> {
            o.setId(10L);
            o.setBuyer(c);
            o.setPaypalOrderId("PP-1");
            o.setStatus(OrderStatus.PENDING_PAYMENT);
            o.setTotalAmount(p.getSalePrice().multiply(BigDecimal.valueOf(qty)));
        });
        addOrderItem(order, p, qty, p.getSalePrice());
        when(ordersRepository.findById(10L)).thenReturn(Optional.of(order));
        return new Pending(order, reservedCodes(p, order, qty));
    }

    /** Igual que stubPendingOrderById, pero además stubea la búsqueda por paypalOrderId (confirmPayment / cancelOrder). */
    private Pending stubPendingOrder(Customer c, Product p, int qty) {
        Pending pe = stubPendingOrderById(c, p, qty);
        when(ordersRepository.findByPaypalOrderIdAndBuyer("PP-1", c)).thenReturn(Optional.of(pe.order()));
        return pe;
    }

    /** Pago válido: captura COMPLETED por el monto exacto de la orden, en MXN. */
    private Pending stubSuccessfulPayment(Customer c, Product p, int qty, Cart cart) {
        Pending pe = stubPendingOrder(c, p, qty);
        when(virtualCodeRepository.findByOrdersId(10L)).thenReturn(pe.codes());
        when(cartRepository.findByCustomerForUpdate(c)).thenReturn(Optional.of(cart));
        when(paypalService.captureOrder("PP-1", CAPTURE_REQUEST_ID))
                .thenReturn(captureJson("COMPLETED", "CAP-1", "COMPLETED", totalOf(pe)));
        return pe;
    }

    private String totalOf(Pending pe) {
        return pe.order().getTotalAmount().toPlainString();
    }

    private JsonNode captureJson(String status, String captureId, String captureStatus, String amount) {
        return captureJson(status, captureId, captureStatus, amount, "MXN");
    }

    /** currency == null => la captura viene sin currency_code. */
    private JsonNode captureJson(String status, String captureId, String captureStatus,
                                 String amount, String currency) {
        String idPart = captureId == null ? "" : "\"id\":\"" + captureId + "\",";
        String currencyPart = currency == null ? "" : "\"currency_code\":\"" + currency + "\",";
        return json("""
                {"status":"%s","purchase_units":[{"payments":{"captures":[
                  {%s"status":"%s","amount":{%s"value":"%s"}}]}}]}
                """.formatted(status, idPart, captureStatus, currencyPart, amount));
    }

    private HttpClientErrorException unprocessable(String body) {
        return httpClientError(HttpStatus.UNPROCESSABLE_ENTITY, body);
    }

    private HttpClientErrorException httpClientError(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(),
                HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    /** Para release/refund: findById(10L) devuelve una orden con ese estado y captureId. */
    private Orders stubOrderById(OrderStatus status, String captureId) {
        Orders o = fakeOrder(x -> {
            x.setId(10L);
            x.setStatus(status);
            x.setPaypalCaptureId(captureId);
        });
        when(ordersRepository.findById(10L)).thenReturn(Optional.of(o));
        return o;
    }
}
package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.CustomerCartNotFoundException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.EmptyCartException;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.QuantityExceededException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.OrderNotFoundException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.OrderNotPayableException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.PaymentNotCompletedException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.PaymentProcessingException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.ReservationExpiredException;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.ProductNotFoundException;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.ItemInCart;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.OrderItem;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.OrdersRepository;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * PayPal checkout for virtual-code products.
 *
 * <p>Two public steps: {@link #checkout(Customer)} reserves the codes and creates the
 * PayPal order; {@link #confirmPayment(String, Customer)} captures the payment.
 * {@link #expireOrReconcile(Long)} is called by a background job to resolve stale or
 * ambiguous orders.
 *
 * <p>Inline tags such as {@code // E10} identify the concurrency and failure scenarios
 * (edge cases) that the surrounding code protects against.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final String CURRENCY = "MXN";

    private final CartRepository cartRepository;
    private final OrdersRepository ordersRepository;
    private final VirtualCodeRepository virtualCodeRepository;
    private final PaypalService paypalService;
    private final TransactionTemplate tx;

    @Value("${orders.expiration.minutes}")
    private int reservationMinutes;

    /**
     * Creates a pending order from the cart, reserves its codes and creates the PayPal order.
     *
     * @param customer authenticated buyer
     * @return the PayPal order id, used by the frontend to open the payment window
     * @throws EmptyCartException        if the cart has no items
     * @throws ProductNotFoundException  if a product in the cart is inactive
     * @throws QuantityExceededException if there are not enough available codes
     * @throws OrderNotPayableException  if a concurrent checkout cancelled this order
     */
    public String checkout(Customer customer) {

        // E5: single transaction, if anything fails it is fully rolled back
        Orders order = tx.execute(s -> createPendingOrder(customer));

        String paypalOrderId;
        try {
            // No waiting on PayPal inside the transaction
            paypalOrderId = paypalService.createOrder(
                    order.getTotalAmount(), CURRENCY, "Pedido #" + order.getId());
        } catch (RuntimeException e) {   // E6
            releaseOrder(order.getId());
            throw e;
        }

        // E7: if the server dies before saving the id, the job releases the order
        tx.executeWithoutResult(s -> {
            Orders o = getOrderOrThrow(order.getId());
            // Only the id is saved, the order stays PENDING
            if (o.getStatus() != OrderStatus.PENDING_PAYMENT)   // E2: another checkout cancelled it
                throw new OrderNotPayableException(o.getId(), o.getStatus());
            o.setPaypalOrderId(paypalOrderId);
        });

        return paypalOrderId;   // Returned to Angular
    }

    /**
     * Locks the cart, cancels older pending orders and reserves one code per unit.
     * Prices always come from the database.
     *
     * @implNote Must be called inside an active transaction.
     */
    private Orders createPendingOrder(Customer customer) {
        // E2: the cart lock queues the second checkout
        Cart cart = cartRepository.findByCustomerForUpdate(customer)
                .orElseThrow(() -> new CustomerCartNotFoundException(customer.getId()));

        if (cart.getItems().isEmpty())
            throw new EmptyCartException();

        // E2: with the cart locked, release the previous pending orders
        ordersRepository.findByBuyerAndStatus(customer, OrderStatus.PENDING_PAYMENT)
                .forEach(o -> cancelPending(o.getId()));

        Orders order = new Orders();
        order.setBuyer(customer);
        order.setTotalAmount(BigDecimal.ZERO);
        order = ordersRepository.save(order);   // the id is needed to link the codes

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(reservationMinutes);
        BigDecimal total = BigDecimal.ZERO;

        // E4: fixed order by productId, so simultaneous purchases don't block each other
        List<ItemInCart> items = cart.getItems().stream()
                .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                .toList();
        // One waits for the other and that's it :)

        for (ItemInCart ci : items) {
            Product p = ci.getProduct();
            int qty = ci.getQuantity();

            if (!p.getActive()) throw new ProductNotFoundException(p.getId());   // E18

            // E3: FOR UPDATE, the second buyer waits for their turn
            List<VirtualCode> codes = virtualCodeRepository.findByProductIdAndStatusForUpdate(
                    p.getId(), CodeStatus.AVAILABLE, PageRequest.of(0, qty));

            // E3, E5: out of stock, roll back all reservations
            if (codes.size() < qty)
                throw new QuantityExceededException(p.getName(), codes.size(), qty);

            for (VirtualCode c : codes) {
                c.setStatus(CodeStatus.RESERVED);
                c.setOrders(order);
                c.setExpiresAt(expiresAt);
            }

            OrderItem oi = new OrderItem();
            oi.setOrders(order);
            oi.setProduct(p);
            oi.setQuantity(qty);
            oi.setUnitPrice(p.getSalePrice());   // E18: price always from the DB
            order.getItems().add(oi);

            total = total.add(p.getSalePrice().multiply(BigDecimal.valueOf(qty)));
        }

        order.setTotalAmount(total);
        return order;
    }

    /**
     * Captures the payment after the user approved it in PayPal.
     * Idempotent: calling it twice for the same order never charges twice.
     *
     * @param paypalOrderId id returned by {@link #checkout(Customer)}
     * @param customer      authenticated buyer; must own the order
     * @return the order, {@code PAID} on success
     * @throws OrderNotFoundException       if the order is not found for this buyer
     * @throws OrderNotPayableException     if the order is no longer pending
     * @throws ReservationExpiredException  if the reserved codes were lost before charging
     * @throws PaymentProcessingException   if the capture result is unknown; the job reconciles
     * @throws PaymentNotCompletedException if PayPal did not report a completed capture
     * @throws HttpClientErrorException     if PayPal rejects the payment; the user can retry
     */
    public Orders confirmPayment(String paypalOrderId, Customer customer) {
        Orders order = ordersRepository
                .findByPaypalOrderIdAndBuyer(paypalOrderId, customer)   // E17: if it is not theirs, 404
                .orElseThrow(OrderNotFoundException::new);

        if (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.REFUNDED) return order;   // E1: idempotent

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT)   // E8: the job already cancelled it, 409
            throw new OrderNotPayableException(order.getId(), order.getStatus());

        // E9, Important 1: before charging, verify the reservation is still alive
        Boolean reservationOk = tx.execute(s -> hasValidReservation(order.getId()));
        if (!Boolean.TRUE.equals(reservationOk)) {
            releaseOrder(order.getId());
            throw new ReservationExpiredException(order.getId());
        }

        JsonNode res;
        try {
            // E1: fixed requestId per order, a retry does not charge twice
            res = paypalService.captureOrder(paypalOrderId, "capture-order-" + order.getId());
            // This is where the charge happens
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            if (e.getResponseBodyAsString().contains("ORDER_ALREADY_CAPTURED"))   // E12
                return waitForOutcome(order.getId());
            throw e;
        } catch (HttpClientErrorException e) {   // E11: definitive rejection, nothing was charged
            throw e;
        } catch (RestClientException e) {
            // E10: timeout or 5xx, we do NOT know whether PayPal charged. Nothing is released,
            // the order stays PENDING_PAYMENT so the job can reconcile it
            log.warn("Capture ambiguous for order {}, left PENDING for reconciliation",
                    order.getId(), e);
            throw new PaymentProcessingException(order.getId());
        }

        return settleCapture(order.getId(), res);
    }

    /**
     * Turns a completed PayPal payment into {@code PAID}, or into {@code FAILED} plus an
     * immediate refund if the order cannot be fulfilled. Used by
     * {@link #confirmPayment(String, Customer)} and by the reconciliation job.
     *
     * @param orderId local order id
     * @param res     PayPal JSON response (capture or order) with the payment
     * @return the order in its final state
     * @throws PaymentNotCompletedException if the capture is not {@code COMPLETED}
     */
    private Orders settleCapture(Long orderId, JsonNode res) {
        JsonNode capture = res.at("/purchase_units/0/payments/captures/0");
        String captureId = capture.path("id").asText(null);

        // E14: must be COMPLETED and the id must exist, otherwise no product is delivered
        if (!"COMPLETED".equals(res.path("status").asText())
                || captureId == null
                || !"COMPLETED".equals(capture.path("status").asText()))
            throw new PaymentNotCompletedException();

        // E13: what PayPal actually charged
        BigDecimal captured = new BigDecimal(capture.at("/amount/value").asText());
        String currency = capture.at("/amount/currency_code").asText();

        // E7: the captureId is saved first, so a refund is always possible
        tx.executeWithoutResult(s ->
                getOrderOrThrow(orderId).setPaypalCaptureId(captureId));

        // Defense 2: wrong amount/currency or lost reservation => charged without product => FAILED
        tx.executeWithoutResult(s -> {
            Orders o = getOrderOrThrow(orderId);

            // E1: another request (or the webhook) already resolved it
            if (o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.REFUNDED) return;

            // E13: compareTo so that 100.0 equals 100.00
            boolean amountOk = CURRENCY.equals(currency)
                    && captured.compareTo(o.getTotalAmount()) == 0;

            // E9, E13: charged but cannot be fulfilled => FAILED
            if (!amountOk || !hasValidReservation(o.getId())) {
                log.error("Order {} charged (paypal {}) but cannot be fulfilled. amountOk={}",
                        o.getId(), o.getPaypalOrderId(), amountOk);
                freeReservedCodes(o.getId());
                o.setStatus(OrderStatus.FAILED);
                return;
            }

            o.setStatus(OrderStatus.PAID);

            LocalDateTime now = LocalDateTime.now();
            for (VirtualCode c : virtualCodeRepository.findByOrdersId(o.getId())) {
                c.setStatus(CodeStatus.SOLD);
                c.setSoldAt(now);
                c.setExpiresAt(null);
            }

            Customer buyer = o.getBuyer();
            Cart cart = cartRepository.findByCustomerForUpdate(buyer)
                    .orElseThrow(() -> new CustomerCartNotFoundException(buyer.getId()));
            cart.getItems().clear();
        });

        Orders result = getOrderOrThrow(orderId);
        if (result.getStatus() == OrderStatus.FAILED) {   // E9, E13: immediate refund
            refundOrder(orderId);
            result = getOrderOrThrow(orderId);
        }
        return result;
    }

    /**
     * Job entry point: completes the order if PayPal already charged it, otherwise cancels
     * it. A pending order is never cancelled unless its state can be verified with PayPal.
     * No-op if the order is not {@code PENDING_PAYMENT}.
     *
     * @param orderId local order id
     */
    public void expireOrReconcile(Long orderId) {
        Orders o = getOrderOrThrow(orderId);
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT) return;

        if (o.getPaypalOrderId() != null) {
            JsonNode res;
            try {
                res = paypalService.getOrder(o.getPaypalOrderId());
            } catch (RuntimeException e) {
                // E10: cannot verify with PayPal, do NOT cancel
                log.warn("Cannot verify order {} with PayPal, will retry", orderId, e);
                return;
            }

            if ("COMPLETED".equals(res.path("status").asText())) {
                // E7: PayPal already charged but the order was still pending, finish it
                log.warn("Order {} was captured in PayPal but still pending, settling", orderId);
                settleCapture(orderId, res);
                return;
            }
        }
        // E7, E8: no charge (or the PayPal order was never created), release it
        releaseOrder(orderId);
    }

    /**
     * Checks that the order is still pending and all its codes are still reserved.
     *
     * @implNote Must be called inside an active transaction.
     */
    private boolean hasValidReservation(Long orderId) {   // E9
        Orders o = getOrderOrThrow(orderId);
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT) return false;

        int expected = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        List<VirtualCode> codes = virtualCodeRepository.findByOrdersId(orderId);

        return codes.size() == expected
                && codes.stream().allMatch(c -> c.getStatus() == CodeStatus.RESERVED);
    }

    /**
     * Cancels a pending order on behalf of its owner and releases its codes.
     * Orders that are not pending (for example, already paid) are left untouched.
     *
     * @param paypalOrderId PayPal order id
     * @param customer      authenticated buyer; must own the order
     * @throws OrderNotFoundException if the order is not found for this buyer
     */
    public void cancelOrder(String paypalOrderId, Customer customer) {
        Orders order = ordersRepository.findByPaypalOrderIdAndBuyer(paypalOrderId, customer)   // E17
                .orElseThrow(OrderNotFoundException::new);
        releaseOrder(order.getId());   // E16
    }

    /** Cancels a pending order and releases its codes in its own transaction. */
    public void releaseOrder(Long orderId) {
        tx.executeWithoutResult(s -> cancelPending(orderId));
    }

    /**
     * Refunds the capture of a {@code FAILED} order and marks it {@code REFUNDED}.
     * Failures are logged and the order stays {@code FAILED}, so the job can retry.
     *
     * @param orderId local order id
     */
    public void refundOrder(Long orderId) {
        Orders o = getOrderOrThrow(orderId);
        // E15: only refunds FAILED orders that have a captureId
        if (o.getStatus() != OrderStatus.FAILED || o.getPaypalCaptureId() == null) return;

        try {
            // E15: fixed requestId per order, if retried PayPal does not duplicate the refund
            String status = paypalService.refundCapture(o.getPaypalCaptureId(), "refund-order-" + orderId);

            if ("COMPLETED".equals(status) || "PENDING".equals(status)) {
                markRefunded(orderId);
            }
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            if (e.getResponseBodyAsString().contains("CAPTURE_FULLY_REFUNDED")) {
                markRefunded(orderId);   // E15: already refunded
            } else {
                log.error("Refund rejected for order {}: {}", orderId, e.getResponseBodyAsString());
            }
        } catch (RuntimeException e) {
            // E15: stays FAILED and the job retries it
            log.error("Refund failed for order {}, will retry", orderId, e);
        }
    }

    /** Sets the order status to {@code REFUNDED} in its own transaction. */
    private void markRefunded(Long orderId) {
        tx.executeWithoutResult(s ->
                getOrderOrThrow(orderId)
                        .setStatus(OrderStatus.REFUNDED));
    }

    /**
     * Returns the order's {@code RESERVED} codes to {@code AVAILABLE}.
     *
     * @implNote Must be called inside an active transaction.
     */
    private void freeReservedCodes(Long orderId) {
        for (VirtualCode c : virtualCodeRepository.findByOrdersId(orderId)) {
            if (c.getStatus() == CodeStatus.RESERVED) {
                c.setStatus(CodeStatus.AVAILABLE);
                c.setOrders(null);
                c.setExpiresAt(null);
            }
        }
    }

    /**
     * Marks a pending order as {@code CANCELLED} and releases its codes.
     * No-op if the order is not {@code PENDING_PAYMENT}.
     *
     * @implNote Must be called inside an active transaction.
     */
    private void cancelPending(Long orderId) {
        Orders o = getOrderOrThrow(orderId);
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT) return;   // E16: do not touch paid orders

        freeReservedCodes(orderId);
        o.setStatus(OrderStatus.CANCELLED);
    }

    /**
     * Waits for a concurrent request to finish processing the same order
     * (5 checks, 400 ms apart).
     *
     * @return the order once it leaves {@code PENDING_PAYMENT}
     * @throws PaymentProcessingException if it is still pending after all checks
     */
    private Orders waitForOutcome(Long orderId) {   // E1, E12
        // Waits 5 times, 400 ms apart, for it to stop being PENDING; if it doesn't change, throws
        for (int i = 0; i < 5; i++) {
            Orders o = getOrderOrThrow(orderId);
            if (o.getStatus() != OrderStatus.PENDING_PAYMENT) return o;
            try {
                Thread.sleep(400);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new PaymentProcessingException(orderId);
    }

    private Orders getOrderOrThrow(Long orderId) {
        return ordersRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
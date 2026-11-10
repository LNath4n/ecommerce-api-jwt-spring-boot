package com.ecommerce.FerreViky.scheduledJobs;

import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.repository.OrdersRepository;
import com.ecommerce.FerreViky.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Periodically reconciles expired pending orders and refunds failed orders.
 */
@Component
@RequiredArgsConstructor
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrdersRepository ordersRepository;
    private final CheckoutService checkoutService;

    @Value("${orders.expiration.minutes}")
    private long expirationMinutes;

    /**
     * Processes pending-payment orders past their expiration limit and attempts
     * to refund orders in the failed state.
     *
     * <p>Errors for individual orders are logged so processing can continue
     * with the remaining orders.</p>
     */
    @Scheduled(fixedDelayString = "${orders.expiration.check-interval-ms}")
    public void releaseExpired() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(expirationMinutes);

        for (Orders o : ordersRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, limit)) {
            try {
                checkoutService.expireOrReconcile(o.getId());
            } catch (Exception e) {
                log.error("Error reconciling order {}", o.getId(), e);
            }
        }

        for (Orders o : ordersRepository.findByStatus(OrderStatus.FAILED)) {
            try {
                checkoutService.refundOrder(o.getId());
            } catch (Exception e) {
                log.error("Error refunding order {}", o.getId(), e);
            }
        }
    }
}
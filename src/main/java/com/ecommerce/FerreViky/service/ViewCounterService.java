
package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.repository.ProductRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Service responsible for tracking product views without performing
 * individual database writes for every visit.
 *
 * <p>Each visit atomically increments a counter in Redis using the
 * {@code INCR} operation. A scheduled task periodically transfers
 * the accumulated counters to the database, performing only one
 * {@code UPDATE} operation per product.</p>
 *
 * <p>If Redis becomes unavailable, product views are temporarily
 * accumulated in a local in-memory buffer and subsequently persisted
 * to the database in batches.</p>
 *
 * <p>This service supports multiple application instances. It uses
 * the atomic {@code GETDEL} operation to ensure that each counter
 * value is retrieved and processed by only one instance.</p>
 */
@Service
@RequiredArgsConstructor
public class ViewCounterService {

    private static final Logger log =
            LoggerFactory.getLogger(ViewCounterService.class);

    /**
     * Prefix used to identify product view counters stored in Redis.
     */
    private static final String PREFIX = "product:views:";

    /**
     * Redis template used to perform atomic operations on product
     * view counters.
     */
    private final StringRedisTemplate redis;

    /**
     * Repository responsible for persisting product view counts
     * in the database.
     */
    private final ProductRepository productRepository;

    /**
     * Local fallback buffer used to temporarily store product views
     * when Redis is unavailable.
     *
     * <p>Each product is associated with a {@link LongAdder} to allow
     * concurrent increments safely and efficiently.</p>
     */
    private final ConcurrentHashMap<Long, LongAdder> localBuffer =
            new ConcurrentHashMap<>();

    /**
     * Registers a new view for the specified product.
     *
     * <p>Attempts to atomically increment the corresponding Redis
     * counter. If Redis is unavailable, the view is stored in the
     * local in-memory buffer instead.</p>
     *
     * <p>This method does not propagate exceptions, ensuring that
     * failures in the view-counting mechanism do not affect the
     * processing of the user's request.</p>
     *
     * @param productId unique identifier of the product being viewed
     */
    public void increment(Long productId) {
        try {
            redis.opsForValue().increment(PREFIX + productId);
        } catch (Exception e) {
            log.debug("Redis unavailable, buffering view of product {}", productId);
            addToLocalBuffer(productId, 1);
        }
    }

    /**
     * Transfers accumulated product view counters from Redis and the
     * local buffer to the database.
     *
     * <p>This method is executed automatically every 30 seconds.
     * It first processes the locally buffered views and then scans
     * Redis for product view counters to persist.</p>
     *
     * <p>Each Redis counter is retrieved using an atomic operation
     * to prevent multiple application instances from processing
     * the same counter value.</p>
     *
     * <p>If an error occurs during synchronization, the process
     * logs a warning and allows the next scheduled execution
     * to retry.</p>
     */
    @Scheduled(fixedDelay = 30_000)
    public void flush() {
        drainLocalBuffer();

        ScanOptions options = ScanOptions.scanOptions()
                .match(PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(this::flushKey);
        } catch (Exception e) {
            log.warn("View flush failed, will retry: {}", e.getMessage());
        }
    }

    /**
     * Persists any product views that remain in the local buffer
     * when the application is shutting down.
     *
     * <p>This method is automatically invoked by Spring before
     * the application context is destroyed.</p>
     */
    @PreDestroy
    void onShutdown() {
        drainLocalBuffer();
    }

    /**
     * Retrieves and persists the accumulated views associated
     * with a specific Redis counter key.
     *
     * <p>The counter is retrieved using the atomic {@code GETDEL}
     * operation, ensuring that only one application instance
     * can obtain and process its value.</p>
     *
     * <p>The Redis key is parsed to extract the product identifier,
     * and its stored value is interpreted as the accumulated
     * number of views.</p>
     *
     * <p>If the database update fails, the retrieved counter value
     * is returned to Redis or stored in the local buffer so that
     * it can be retried during the next synchronization cycle.</p>
     *
     * @param key Redis key containing the accumulated product views
     */
    private void flushKey(String key) {

        // Atomic operation: if two instances request the same key,
        // only one retrieves its value while the other receives null.
        String raw = redis.opsForValue().getAndDelete(key);

        if (raw == null) return;

        long productId;
        long delta;

        try {
            productId = Long.parseLong(key.substring(PREFIX.length()));
            delta = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid view counter {} = {}", key, raw);
            return;
        }

        if (delta <= 0) return;

        try {
            productRepository.addViews(productId, delta);
        } catch (Exception e) {
            log.warn("Could not save {} views of product {}, returning them", delta, productId);
            giveBack(key, productId, delta);
        }
    }

    /**
     * Restores a counter value to Redis when its database update fails.
     *
     * <p>If Redis is still unavailable, the value is added to the
     * local in-memory buffer to prevent the accumulated views
     * from being lost.</p>
     *
     * @param key       original Redis key associated with the counter
     * @param productId identifier of the product whose views are being restored
     * @param delta     number of views that could not be persisted
     */
    private void giveBack(String key, long productId, long delta) {
        try {
            redis.opsForValue().increment(key, delta);
        } catch (Exception e) {
            addToLocalBuffer(productId, delta);
        }
    }

    /**
     * Transfers all accumulated views from the local buffer to the database.
     *
     * <p>Each counter is atomically retrieved and reset using
     * {@link LongAdder#sumThenReset()}, allowing new concurrent
     * increments to continue without blocking.</p>
     *
     * <p>If a database update fails, the corresponding number of views
     * is restored to the local counter so that it can be processed
     * during the next synchronization cycle.</p>
     */
    private void drainLocalBuffer() {
        localBuffer.forEach((id, adder) -> {
            long views = adder.sumThenReset();

            if (views <= 0) return;

            try {
                productRepository.addViews(id, views);
            } catch (Exception e) {
                adder.add(views);   // Restore the views for the next synchronization cycle.
            }
        });
    }

    /**
     * Adds a specified number of views to the local in-memory buffer.
     *
     * <p>If a counter does not already exist for the specified product,
     * a new {@link LongAdder} is created automatically.</p>
     *
     * @param productId identifier of the product whose views are being buffered
     * @param amount    number of views to add to the counter
     */
    private void addToLocalBuffer(Long productId, long amount) {
        localBuffer.computeIfAbsent(productId, k -> new LongAdder()).add(amount);
    }
}

package com.ecommerce.FerreViky.ratelimit.provider;

import com.ecommerce.FerreViky.ratelimit.RateLimitType;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Usa Redis y, si falla, cae a Caffeine (límite por instancia).
 * Mientras el "circuito" esté abierto ni siquiera intenta Redis, para no
 * pagar el timeout en cada request.
 *
 * Las llamadas a Redis ocurren cuando el filtro hace tryConsume..., no en bucketFor,
 * por eso devolvemos un proxy de Bucket que decide en cada llamada.
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "app.ratelimit.redis.enabled", havingValue = "true")
public class ResilientBucketProvider implements BucketProvider {

    private static final long OPEN_NANOS = Duration.ofSeconds(10).toNanos();

    private final RedisBucketProvider redis;
    private final CaffeineBucketProvider fallback;

    // System.nanoTime() hasta el que se omite Redis (inicia en "ya vencido")
    private volatile long openUntil = System.nanoTime();
    private final AtomicBoolean open = new AtomicBoolean(false);

    public ResilientBucketProvider(RedisBucketProvider redis, CaffeineBucketProvider fallback) {
        this.redis = redis;
        this.fallback = fallback;
    }

    @Override
    public Bucket bucketFor(String key, RateLimitType type) {
        return (Bucket) Proxy.newProxyInstance(
                Bucket.class.getClassLoader(),
                new Class<?>[]{Bucket.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }

                    if (redisAvailable()) {
                        try {
                            Bucket primary = redis.bucketFor(key, type);
                            Object result = method.invoke(primary, args);
                            recovered();
                            return result;
                        } catch (InvocationTargetException e) {
                            tripCircuit(e.getCause());
                        } catch (RuntimeException e) {
                            tripCircuit(e);
                        }
                    }

                    try {
                        return method.invoke(fallback.bucketFor(key, type), args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private boolean redisAvailable() {
        return System.nanoTime() - openUntil >= 0;
    }

    private void tripCircuit(Throwable cause) {
        openUntil = System.nanoTime() + OPEN_NANOS;
        if (open.compareAndSet(false, true)) {
            log.warn("Redis no disponible para rate limiting, usando Caffeine por instancia: {}",
                    cause == null ? "?" : cause.toString());
        }
    }

    private void recovered() {
        if (open.compareAndSet(true, false)) {
            log.info("Redis disponible de nuevo para rate limiting");
        }
    }
}
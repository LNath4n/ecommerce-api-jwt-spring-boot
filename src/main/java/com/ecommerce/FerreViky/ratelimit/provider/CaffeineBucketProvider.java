package com.ecommerce.FerreViky.ratelimit.provider;

import com.ecommerce.FerreViky.ratelimit.RateLimitType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CaffeineBucketProvider implements BucketProvider {

    // Debe ser mayor que el periodo más largo de RateLimitType (RESEND_EMAIL = 1 hora),
    // si no el bucket se borraría (y se reiniciaría) antes de recargarse.
    // maximumSize acota la memoria.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(50_000)
            .build();

    @Override
    public Bucket bucketFor(String key, RateLimitType type) {
        // El tipo va en la clave para que cada tipo tenga su propio bucket
        // Al final la cubeta se ve algo asi "AUTH:ip:192.168.1.5"
        // Bandwidth: al terminar el periodo del tipo se rellena de golpe
        return buckets.get(type.name() + ":" + key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(type.getCapacity(),
                        Refill.intervally(type.getCapacity(), type.getPeriod())))
                .build());
    }
}
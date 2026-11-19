package com.ecommerce.FerreViky.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Checks the API status")
public class HealthController {

    private static final String VERSION = "4.1";
    private static final String FEATURED = "We added Paypal :)";
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final RedisConnectionFactory redisConnectionFactory;
    private final DataSource dataSource;

    public record HealthResponse(
            String version,
            String featured,
            String status,
            String database,
            String redis,
            Instant timestamp
    ) {}

    @GetMapping
    @Operation(summary = "Get status", description = "Returns API version, featured update and dependency status")
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp = isDbUp();
        boolean redisUp = isRedisUp();

        HealthResponse body = new HealthResponse(
                VERSION,
                FEATURED,
                dbUp ? (redisUp ? "UP" : "DEGRADED") : "DOWN",
                dbUp ? "UP" : "DOWN",
                redisUp ? "UP" : "DOWN",
                Instant.now()
        );

        // Sin Redis la app sigue funcionando (solo sin caché), así que no devuelvo 503
        return ResponseEntity.status(dbUp ? 200 : 503).body(body);
    }

    private boolean isRedisUp() {
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(conn.ping());
        } catch (Exception e) {
            log.warn("Redis health check falló: {}", e.toString());
            return false;
        }
    }

    private boolean isDbUp() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
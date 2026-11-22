package com.ecommerce.FerreViky.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Checks the API status")
public class HealthController {

    private static final String VERSION = "5.1";
    private static final String FEATURED = "Add email verification with DynamoDB";
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final RedisConnectionFactory redisConnectionFactory;
    private final DataSource dataSource;
    private final DynamoDbClient dynamoDbClient;

    @Value("${app.dynamodb.verification-table}")
    private String verificationTable;

    @Value("${APP_COMMIT:unknown}")
    private String commit;

    @Value("${APP_DEPLOY:0}")
    private String deploy;

    public record HealthResponse(
            String version,
            String deploy,
            String commit,
            String featured,
            String status,
            String database,
            String redis,
            String dynamodb,
            Instant timestamp
    ) {}

    @GetMapping
    @Operation(summary = "Get status", description = "Returns API version, featured update and dependency status")
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp = isDbUp();
        boolean redisUp = isRedisUp();
        boolean dynamoUp = isDynamoUp();

        HealthResponse body = new HealthResponse(
                VERSION,
                deploy,
                commit,
                FEATURED,
                dbUp ? (redisUp && dynamoUp ? "UP" : "DEGRADED") : "DOWN",
                dbUp ? "UP" : "DOWN",
                redisUp ? "UP" : "DOWN",
                dynamoUp ? "UP" : "DOWN",
                Instant.now()
        );

        // Sin Redis o sin DynamoDB la app sigue funcionando (sin caché / sin verificación de correo), así que no devuelvo 503
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

    private boolean isDynamoUp() {
        try {
            dynamoDbClient.describeTable(r -> r
                    .tableName(verificationTable)
                    .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(2))));
            return true;
        } catch (Exception e) {
            log.warn("DynamoDB health check falló: {}", e.toString());
            return false;
        }
    }
}
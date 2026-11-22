package com.ecommerce.FerreViky.configuration;

import com.ecommerce.FerreViky.models.VerificationToken;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

/** Crea la tabla y activa el TTL. Solo corre cuando app.dynamodb.create-table=true (dev). */
@Component
@ConditionalOnProperty(name = "app.dynamodb.create-table", havingValue = "true")
@RequiredArgsConstructor
public class DynamoDbTableInitializer {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient client;

    @Value("${app.dynamodb.verification-table}")
    private String tableName;

    @PostConstruct
    void init() {
        DynamoDbTable<VerificationToken> table =
                enhancedClient.table(tableName, TableSchema.fromBean(VerificationToken.class));
        try {
            table.createTable();
        } catch (ResourceInUseException ignored) {
            // la tabla ya existe
        }

        client.updateTimeToLive(r -> r.tableName(tableName)
                .timeToLiveSpecification(s -> s.enabled(true).attributeName("expiresAt")));
    }
}
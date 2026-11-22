package com.ecommerce.FerreViky.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class VerificationToken {

    private String tokenHash;
    private Long customerId;
    private Long expiresAt; // es el atributo TTL

    @DynamoDbPartitionKey
    public String getTokenHash() {
        return tokenHash;
    }
}
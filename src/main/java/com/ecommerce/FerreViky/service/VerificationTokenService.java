package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.InvalidVerificationTokenException;
import com.ecommerce.FerreViky.models.VerificationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Service responsible for generating and validating single-use email
 * verification tokens, stored in DynamoDB.
 *
 * <p>Tokens are generated from 32 random bytes using {@link SecureRandom}
 * and encoded as URL-safe Base64. The plain-text token is only ever sent
 * to the customer by email; the database stores its SHA-256 hash, so a
 * leak of the table does not expose usable tokens.</p>
 *
 * <p>Each token can be used only once. Consuming a token atomically
 * deletes it from the table, which prevents the same link from being
 * used twice, even by concurrent requests or multiple application
 * instances.</p>
 *
 * <p>Expiration is enforced by this service and does not rely on the
 * DynamoDB TTL feature, which can take hours to remove expired items.</p>
 */
@Service
public class VerificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(VerificationTokenService.class);

    /**
     * Cryptographically secure random generator used to create tokens.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * DynamoDB table where the hashed tokens are stored.
     */
    private final DynamoDbTable<VerificationToken> table;

    /**
     * Time a token remains valid after being created.
     */
    private final Duration ttl;

    /**
     * Creates the service and binds it to the verification table.
     *
     * @param enhancedClient DynamoDB enhanced client used to access the table
     * @param tableName      table name, from {@code app.dynamodb.verification-table}
     * @param ttlMinutes     token validity in minutes, from
     *                       {@code app.verification.token-ttl-minutes}
     */
    public VerificationTokenService(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.dynamodb.verification-table}") String tableName,
            @Value("${app.verification.token-ttl-minutes}") long ttlMinutes) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(VerificationToken.class));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * Generates a new verification token for the specified customer.
     *
     * <p>The token is built from 32 random bytes encoded as URL-safe Base64
     * without padding. Only its SHA-256 hash is persisted, together with
     * the customer identifier and the expiration time (epoch seconds).</p>
     *
     * <p>The returned plain-text value is not stored anywhere and must be
     * delivered to the customer right away (see
     * {@link EmailService#sendVerificationEmail}).</p>
     *
     * @param customerId identifier of the customer to be verified
     * @return the token in plain text, to be included in the email link
     */
    public String create(Long customerId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        VerificationToken item = new VerificationToken();
        item.setTokenHash(hash(rawToken));
        item.setCustomerId(customerId);
        item.setExpiresAt(Instant.now().plus(ttl).getEpochSecond());
        table.putItem(item);

        log.debug("Verification token created for customer {}", customerId);
        return rawToken;
    }

    /**
     * Validates and consumes a verification token.
     *
     * <p>The token is hashed and the matching item is deleted from the
     * table in a single atomic operation, so a token can be consumed only
     * once. Because the item is deleted even when it has already expired,
     * expired tokens are also cleaned up as a side effect.</p>
     *
     * <p>Expiration is validated here because the DynamoDB TTL can take
     * hours to remove expired items.</p>
     *
     * @param rawToken token in plain text, as received from the email link
     * @return identifier of the customer the token was issued for
     * @throws InvalidVerificationTokenException if the token does not exist,
     *                                           was already used, or has expired
     */
    public Long consume(String rawToken) {
        VerificationToken deleted = table.deleteItem(
                Key.builder().partitionValue(hash(rawToken)).build());

        // DynamoDB TTL may take hours to remove items: expiration is validated here.
        if (deleted == null || deleted.getExpiresAt() < Instant.now().getEpochSecond()) {
            log.warn("Verification token rejected: not found or expired");
            throw new InvalidVerificationTokenException();
        }
        return deleted.getCustomerId();
    }

    /**
     * Computes the SHA-256 hash of a token, as a lowercase hexadecimal string.
     *
     * <p>This is the value used as the partition key in DynamoDB.</p>
     *
     * @param token token in plain text
     * @return hexadecimal SHA-256 digest of the token
     * @throws IllegalStateException if SHA-256 is not available in the JVM
     *                               (should never happen, as it is mandatory
     *                               in every Java platform implementation)
     */
    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
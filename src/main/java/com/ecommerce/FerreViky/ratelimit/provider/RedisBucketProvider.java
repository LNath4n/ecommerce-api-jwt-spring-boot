package com.ecommerce.FerreViky.ratelimit.provider;

import com.ecommerce.FerreViky.ratelimit.RateLimitType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Buckets compartidos entre instancias, guardados en Redis.
 * Solo se crea si app.ratelimit.redis.enabled=true.
 * La conexión es perezosa: si Redis está caído al arrancar, la app arranca igual
 * y ResilientBucketProvider se encarga de usar Caffeine.
 */
@Component
@ConditionalOnProperty(name = "app.ratelimit.redis.enabled", havingValue = "true")
public class RedisBucketProvider implements BucketProvider {

    private final RedisURI uri;
    private final Duration timeout;

    private RedisClient client;
    private StatefulRedisConnection<String, byte[]> connection;
    private volatile LettuceBasedProxyManager<String> proxyManager;

    public RedisBucketProvider(@Value("${spring.data.redis.host:localhost}") String host,
                               @Value("${spring.data.redis.port:6379}") int port,
                               @Value("${spring.data.redis.password:}") String password,
                               @Value("${spring.data.redis.ssl.enabled:false}") boolean ssl,
                               @Value("${spring.data.redis.timeout:500ms}") Duration timeout) {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withSsl(ssl)
                .withTimeout(timeout);
        if (StringUtils.hasText(password)) {
            builder.withPassword(password.toCharArray());
        }
        this.uri = builder.build();
        this.timeout = timeout;
    }

    @Override
    public Bucket bucketFor(String key, RateLimitType type) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(type.getCapacity(),
                        Refill.intervally(type.getCapacity(), type.getPeriod())))
                .build();

        // Misma clave que Caffeine: "AUTH:ip:192.168.1.5"
        return proxy().builder().build(type.name() + ":" + key, () -> config);
    }

    private LettuceBasedProxyManager<String> proxy() {
        LettuceBasedProxyManager<String> pm = proxyManager;
        if (pm != null) return pm;

        synchronized (this) {
            if (proxyManager == null) {
                RedisClient c = RedisClient.create(uri);
                try {
                    c.setOptions(ClientOptions.builder()
                            .autoReconnect(true)
                            // Si no hay conexión, falla rápido en vez de encolar comandos
                            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                            .timeoutOptions(TimeoutOptions.enabled(timeout))
                            .build());
                    c.setDefaultTimeout(timeout);

                    StatefulRedisConnection<String, byte[]> conn =
                            c.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

                    // TTL de la clave = tiempo que tarda en recargarse el bucket + 2 min.
                    // Se adapta solo al periodo de cada tipo (1 min, 1 hora...).
                    LettuceBasedProxyManager<String> built = LettuceBasedProxyManager.builderFor(conn)
                            .withExpirationStrategy(
                                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                            .build();

                    client = c;
                    connection = conn;
                    proxyManager = built;
                } catch (RuntimeException e) {
                    // Evita fugar un RedisClient por cada reintento fallido
                    c.shutdown();
                    throw e;
                }
            }
            return proxyManager;
        }
    }

    @PreDestroy
    void shutdown() {
        if (connection != null) connection.close();
        if (client != null) client.shutdown();
    }
}
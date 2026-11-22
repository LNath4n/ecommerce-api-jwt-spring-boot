package com.ecommerce.FerreViky.ratelimit;

import com.ecommerce.FerreViky.ratelimit.provider.BucketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.ecommerce.FerreViky.jwt.JwtService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final BucketProvider bucketProvider;
    private final JwtService jwtService;

    @Value("${app.origin.secret:}")
    private String originSecret;

    @Value("${app.ratelimit.enabled:true}")
    private boolean enabled;


    private String clientIp(HttpServletRequest request) {
        if (viaCloudFront(request)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                String[] partes = xff.split(",");
                String ip = partes[partes.length - 1].trim();   // la que añadió CloudFront // the one added by CloudFront
                log.debug("Client IP {} taken from X-Forwarded-For (CloudFront), full header: {}", ip, xff);
                return ip;
            }
            log.warn("Request verified as CloudFront but X-Forwarded-For is missing, using remote address {}",
                    request.getRemoteAddr());
        }
        String remote = request.getRemoteAddr();
        log.debug("Client IP {} taken from remote address (not via CloudFront)", remote);
        return remote;
    }

    private boolean viaCloudFront(HttpServletRequest request) {
        if (!StringUtils.hasText(originSecret)) return false;   // en local no aplica // does not apply locally
        String recibido = request.getHeader("X-Origin-Verify");
        //MessageDigest.isEqua es seguro contra ataques de tiempo que intenten adivinar el secreto // MessageDigest.isEqual is safe against timing attacks that try to guess the secret
        boolean verified = recibido != null && MessageDigest.isEqual(
                recibido.getBytes(StandardCharsets.UTF_8),
                originSecret.getBytes(StandardCharsets.UTF_8));
        if (recibido == null) {
            log.debug("No X-Origin-Verify header, request is not treated as coming from CloudFront");
        } else if (!verified) {
            // Only the fact is logged, never the received value or the configured secret
            log.warn("Invalid X-Origin-Verify header received from remote address {}", request.getRemoteAddr());
        }
        return verified;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/health".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        RateLimitType type = typeFor(request);
        String key = clientKey(request, type);
        Bucket bucket = bucketProvider.bucketFor(key, type);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        log.debug("Rate limit check: {} {} type={} key={} consumed={} remaining={}",
                request.getMethod(), request.getServletPath(), type, key,
                probe.isConsumed(), probe.getRemainingTokens());

        if (!probe.isConsumed()) {

            long retryAfter = Math.max(1L, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
            log.warn("Rate limit exceeded: key={} type={} {} {} retryAfter={}s",
                    key, type, request.getMethod(), request.getServletPath(), retryAfter);

            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");

            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitType typeFor(HttpServletRequest request) {
        String path = request.getServletPath();
        //El orden importa por que admin y admin/image llevan admin // Order matters because admin and admin/image both contain admin
        // resend-verification va antes que /auth porque tiene su propio límite, más estricto
        if (path.equals("/auth/resend-verification")) return RateLimitType.RESEND_IP;
        if (path.startsWith("/auth")) return RateLimitType.AUTH;
        if (path.equals("/customer/photo") || path.equals("/admin/products/image")) return RateLimitType.UPLOAD;
        if (path.startsWith("/admin")) return RateLimitType.ADMIN;
        if (path.startsWith("/products")) return RateLimitType.PUBLIC;
        return RateLimitType.USER; // /cart, /customer/**, etc.
    }

    private String clientKey(HttpServletRequest request, RateLimitType type) {

        if (type == RateLimitType.AUTH || type == RateLimitType.RESEND_IP) {
            return "ip:" + clientIp(request); //No hay sesion entonces IP // There is no session, so use the IP
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            try {
                String username = jwtService.getUsernameFromToken(authHeader.substring(7));
                if (StringUtils.hasText(username)) {
                    return "user:" + username;
                }
            } catch (JwtException e) {
                // Token inválido: cae a la IP // Invalid token: falls back to the IP
                // Only the exception type is logged, never the token
                log.debug("Invalid JWT ({}), falling back to IP for rate limit key", e.getClass().getSimpleName());
            }
        }

        return "ip:" + clientIp(request);
    }
}
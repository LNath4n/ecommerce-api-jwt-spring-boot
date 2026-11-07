package com.ecommerce.FerreViky.ratelimit;

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

    private final BucketProvider bucketProvider;
    private final JwtService jwtService;

    @Value("${app.origin.secret:}")
    private String originSecret;

    private String clientIp(HttpServletRequest request) {
        if (viaCloudFront(request)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                String[] partes = xff.split(",");
                return partes[partes.length - 1].trim();   // la que añadió CloudFront
            }
        }
        System.out.println("Hola desde : "+request.getRemoteAddr());
        return request.getRemoteAddr();
    }

    private boolean viaCloudFront(HttpServletRequest request) {
        if (!StringUtils.hasText(originSecret)) return false;   // en local no aplica
        String recibido = request.getHeader("X-Origin-Verify");
        //MessageDigest.isEqua es seguro contra ataques de tiempo que intenten adivinar el secreto
        return recibido != null && MessageDigest.isEqual(
                recibido.getBytes(StandardCharsets.UTF_8),
                originSecret.getBytes(StandardCharsets.UTF_8));
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // OPTIONS (preflight CORS) y /health (monitoreo / healthchecks) no consumen cuota
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || "/health".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        RateLimitType type = typeFor(request);
        Bucket bucket = bucketProvider.bucketFor(clientKey(request, type), type);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {

            long retryAfter = Math.max(1L, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);

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
        //El orden importa por que admin y admin/image llevan admin
        if (path.startsWith("/auth")) return RateLimitType.AUTH;
        if (path.equals("/customer/photo") || path.equals("/admin/products/image")) return RateLimitType.UPLOAD;
        if (path.startsWith("/admin")) return RateLimitType.ADMIN;
        if (path.startsWith("/products")) return RateLimitType.PUBLIC;
        return RateLimitType.USER; // /cart, /customer/**, etc.
    }

    private String clientKey(HttpServletRequest request, RateLimitType type) {

        if (type == RateLimitType.AUTH) {
            return "ip:" + clientIp(request); //No hay sesion entonces IP
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            try {
                String username = jwtService.getUsernameFromToken(authHeader.substring(7));
                if (StringUtils.hasText(username)) {
                    return "user:" + username;
                }
            } catch (JwtException ignored) {
                // Token inválido: cae a la IP
            }
        }

        return "ip:" + clientIp(request);
    }
}
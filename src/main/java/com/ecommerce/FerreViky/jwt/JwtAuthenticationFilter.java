package com.ecommerce.FerreViky.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that runs once per HTTP request.
 *
 * <p>Registered before
 * {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter}
 * in Spring Security's filter chain. It:</p>
 * <ol>
 *   <li>Extracts the JWT from the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Validates the token and retrieves the customer's username (email).</li>
 *   <li>Loads {@link UserDetails} and, for a valid token, sets authentication in
 *       the {@link SecurityContextHolder} so protected endpoints can access the
 *       principal through {@code @AuthenticationPrincipal}.</li>
 * </ol>
 * If the header is missing or the token is invalid, the request continues
 * unauthenticated and Spring Security rejects it if authentication is required.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter { //Se ejecuta una vez por cada request antes del controller

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth", "/products", "/health", "/error"
    );

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Determines whether the filter should be skipped for the current request.
     *
     * <p>Skips {@code OPTIONS} requests (CORS preflight) and the public routes in
     * {@link #PUBLIC_PATHS}, so an invalid or expired bearer token does not affect
     * endpoints that do not require authentication. Keep this list synchronized
     * with {@code permitAll()} in the {@code SecurityFilterChain}.</p>
     *
     * @param request incoming HTTP request
     * @return {@code true} if the filter should be skipped
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getServletPath();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Extracts and validates the JWT, then sets authentication in the security
     * context for the rest of the filter chain when the token is valid.
     *
     * @param request incoming HTTP request
     * @param response HTTP response
     * @param filterChain filter chain to continue after processing
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        //Lo que recibimos, lo que regresaremos y la fila de filtros

        final String token = getTokenFromRequest(request);
        final String username;

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            username = jwtService.getUsernameFromToken(token);
        } catch (JwtException e) { //Aqui atrapamos la excepcion de getAllClaims
            filterChain.doFilter(request, response);
            return;
        }

        // Solo autentica si hay username y aún no hay autenticación activa en el contexto
        // (evita re-autenticar en cada filtro si ya fue procesado antes en la misma petición)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try { //Try en caso de que alguien tenga un token pero ya no exista su usaurio en BD
                userDetails = userDetailsService.loadUserByUsername(username); //Se verifica el Rol con la BD
            } catch (UsernameNotFoundException e) {
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()); //De aqui sacamos el Rol
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT from the {@code Authorization} header.
     *
     * <p>Accepts only the {@code Bearer} scheme. If the header is absent or has
     * another format, returns {@code null} and the filter continues without
     * authenticating the request.</p>
     *
     * @param request HTTP request containing the header
     * @return the JWT without the {@code "Bearer "} prefix, or {@code null} if not applicable
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
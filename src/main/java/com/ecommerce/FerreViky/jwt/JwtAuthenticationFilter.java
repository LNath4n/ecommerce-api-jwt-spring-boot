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

/**
 * Filtro de autenticación JWT que se ejecuta una sola vez por petición HTTP.
 * <p>
 * Se registra antes de {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter}
 * en la cadena de filtros de Spring Security. Su responsabilidad es:
 * <ol>
 *   <li>Extraer el token JWT del header {@code Authorization: Bearer <token>}.</li>
 *   <li>Validar el token y obtener el username (email) del cliente.</li>
 *   <li>Cargar el {@link UserDetails} y, si el token es válido, inyectar la autenticación
 *       en el {@link SecurityContextHolder} para que los endpoints protegidos puedan acceder
 *       al principal via {@code @AuthenticationPrincipal}.</li>
 * </ol>
 * Si el header no existe o el token no es válido, la petición continúa sin autenticación
 * y Spring Security la rechazará si el endpoint lo requiere.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter { //Se ejecuta una vez por cada request antes del controller

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Lógica principal del filtro. Extrae y valida el JWT; si es válido, establece
     * la autenticación en el contexto de seguridad para el resto de la cadena.
     *
     * @param request     petición HTTP entrante
     * @param response    respuesta HTTP
     * @param filterChain cadena de filtros a la que se le pasa el control al terminar
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // Solo autentica si hay username y aún no hay autenticación activa en el contexto
        // (evita re-autenticar en cada filtro si ya fue procesado antes en la misma petición)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try { //Try en caso de que alguien tenga un token pero ya no exista su usaurio en BD
                userDetails = userDetailsService.loadUserByUsername(username); //Se verifica el Rol con la BD
            } catch (UsernameNotFoundException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
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
     * Extrae el token JWT crudo del header {@code Authorization}.
     * <p>
     * Solo acepta el esquema {@code Bearer}: si el header tiene otro formato o está ausente,
     * retorna {@code null} y el filtro deja pasar la petición sin autenticar.
     *
     * @param request petición HTTP de la que se extrae el header
     * @return el token JWT sin el prefijo {@code "Bearer "}, o {@code null} si no aplica
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
package com.ecommerce.FerreViky.jwt;

import com.ecommerce.FerreViky.models.Cliente;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Servicio encargado de generar, parsear y validar tokens JWT.
 * <p>
 * Usa HMAC-SHA256 con una clave secreta configurada en {@code application.properties}
 * bajo la propiedad {@code jwt.secret-key} (Base64-encoded).
 * Los tokens tienen una vigencia de 24 minutos desde su emisión.
 */
@Service
public class JwtService {

    @Value("${jwt.secret-key}")
    private String SECRET_KEY;

    /**
     * Construye la clave de firma HMAC a partir del secreto Base64 configurado.
     * Se llama en cada operación de firma/parseo para no mantener la clave en memoria
     * más tiempo del necesario.
     *
     * @return {@link Key} lista para firmar o verificar con HMAC-SHA256
     */
    private Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Genera un token JWT para el cliente sin claims adicionales.
     *
     * @param cliente cliente autenticado cuyo username (email) se usa como subject
     * @return token JWT firmado
     */
    public String getToken(Cliente cliente) {
        return getToken(new HashMap<>(), cliente);
    }

    /**
     * Genera un token JWT con claims personalizados adicionales.
     * <p>
     * El token incluye:
     * <ul>
     *   <li>Los {@code extraClaims} proporcionados (pueden estar vacíos).</li>
     *   <li>{@code sub}: username del cliente (email).</li>
     *   <li>{@code iat}: timestamp de emisión.</li>
     *   <li>{@code exp}: timestamp de expiración (24 minutos desde emisión).</li>
     * </ul>
     *
     * @param extraClaims claims adicionales a incluir en el payload del token
     * @param cliente     cliente cuyo username se usa como subject
     * @return token JWT compacto y firmado con HMAC-SHA256
     */
    public String getToken(Map<String, Object> extraClaims, Cliente cliente) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(cliente.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 24))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extrae el username (email del cliente) del claim {@code sub} del token.
     *
     * @param token JWT del que se extrae el subject
     * @return username contenido en el token
     */
    public String getUsernameFromToken(String token) {
        return getClaim(token, Claims::getSubject);
    }

    /**
     * Valida que el token sea auténtico y no haya expirado para el usuario dado.
     *
     * @param token       JWT a validar
     * @param userDetails detalles del usuario cargado desde la BD
     * @return {@code true} si el username del token coincide con el del usuario y el token no ha expirado
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * Parsea y retorna todos los claims del token, verificando la firma con la clave secreta.
     * Lanza excepción si el token es inválido, está malformado o la firma no coincide.
     *
     * @param token JWT a parsear
     * @return {@link Claims} con el payload completo del token
     */
    private Claims getAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Extrae un claim específico del token aplicando una función resolutora.
     * <p>
     * Ejemplo de uso: {@code getClaim(token, Claims::getSubject)} para obtener el username.
     *
     * @param token          JWT del que se extraen los claims
     * @param claimsResolver función que mapea los claims al valor deseado
     * @param <T>            tipo del valor retornado
     * @return el claim extraído y transformado
     */
    public <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Obtiene la fecha de expiración del token.
     *
     * @param token JWT del que se extrae la expiración
     * @return fecha de expiración del token
     */
    private Date getExpiration(String token) {
        return getClaim(token, Claims::getExpiration);
    }

    /**
     * Verifica si el token ya expiró comparando su fecha de expiración con la fecha actual.
     *
     * @param token JWT a evaluar
     * @return {@code true} si el token ya expiró
     */
    private boolean isTokenExpired(String token) {
        return getExpiration(token).before(new Date());
    }
}
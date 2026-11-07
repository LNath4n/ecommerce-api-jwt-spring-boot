package com.ecommerce.FerreViky.jwt;

import com.ecommerce.FerreViky.models.Customer;
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
 * Service for issuing, parsing, and validating JWTs.
 *
 * <p>Uses HMAC-SHA256 with a Base64-encoded secret configured in
 * {@code application.properties} under {@code jwt.secret-key}. Tokens expire
 * 24 minutes after issuance.</p>
 */
@Service
public class JwtService {

    @Value("${jwt.secret-key}")
    private String SECRET_KEY;

    /**
     * Builds the HMAC signing key from the configured Base64 secret.
     *
     * @return a {@link Key} for signing or verifying with HMAC-SHA256
     */
    private Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a JWT for a customer without additional claims.
     *
     * @param customer authenticated customer whose email is used as the subject
     * @return signed JWT
     */
    public String getToken(Customer customer) {
        Map<String, Object> extraClaims = new HashMap<>();
        // The name is returned by the profile endpoint, so it is not duplicated in the JWT. / El nombre se devuelve en el endpoint del perfil y no se duplica en el JWT.
        extraClaims.put("role", customer.getRole().name()); // "ADMIN" or "USER" / "ADMIN" o "USER"
        extraClaims.put("uid", customer.getId()); // Prevents access with an old email address. / Evita el acceso con un correo anterior.
        extraClaims.put("tv", customer.getTokenVersion()); // Invalidates prior tokens after a password change. / Invalida los tokens anteriores al cambiar la contraseña.
        return getToken(extraClaims, customer);
    }

    /**
     * Generates a JWT with additional custom claims.
     *
     * <p>The token includes:</p>
     * <ul>
     *   <li>The supplied {@code extraClaims} (which may be empty).</li>
     *   <li>{@code sub}: the customer's username (email).</li>
     *   <li>{@code iat}: the issuance timestamp.</li>
     *   <li>{@code exp}: the expiration timestamp (24 minutes after issuance).</li>
     * </ul>
     *
     * @param extraClaims additional claims to include in the token payload
     * @param customer customer whose username is used as the subject
     * @return a compact JWT signed with HMAC-SHA256
     */
    public String getToken(Map<String, Object> extraClaims, Customer customer) {
        return Jwts.builder()
                .setClaims(extraClaims) //Datos extra
                .setSubject(customer.getUsername()) //sub = email
                .setIssuedAt(new Date(System.currentTimeMillis())) // iat = cuando se creo
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 24)) //exp = cuando vence
                .signWith(getKey(), SignatureAlgorithm.HS256) // firma
                .compact(); //toString
    }

    /**
     * Extracts the username (customer email) from the token's {@code sub} claim.
     *
     * @param token JWT containing the subject
     * @return the username in the token
     */
    public String getUsernameFromToken(String token) {
        return getClaim(token, Claims::getSubject);
    }

    /**
     * Validates that the token is authentic and has not expired for the given user.
     *
     * @param token JWT to validate
     * @param userDetails user loaded from the database
     * @return {@code true} if the token username matches the user and the token is not expired
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        Long uid = getClaim(token, c -> c.get("uid", Long.class));
        Integer tv = getClaim(token, c -> c.get("tv", Integer.class));

        if (!(userDetails instanceof Customer customer)) return false;

        return username.equals(userDetails.getUsername())
                && uid != null && uid.equals(customer.getId())
                && tv != null && tv == customer.getTokenVersion()
                && !isTokenExpired(token)
                && userDetails.isEnabled();
    }

    /**
     * Parses all token claims and verifies the signature with the secret key.
     * Throws an exception if the token is invalid, malformed, or has a
     * mismatched signature.
     *
     * @param token JWT to parse
     * @return {@link Claims} containing the full token payload
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
     * Extracts a specific claim by applying a resolver function.
     *
     * <p>For example, use {@code getClaim(token, Claims::getSubject)} to get the username.</p>
     *
     * @param token JWT containing the claims
     * @param claimsResolver function that maps the claims to the desired value
     * @param <T> type of the returned value
     * @return the extracted and transformed claim
     */
    public <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Gets the token's expiration date.
     *
     * @param token JWT containing the expiration
     * @return the token's expiration date
     */
    private Date getExpiration(String token) {
        return getClaim(token, Claims::getExpiration);
    }

    /**
     * Checks whether the token has expired by comparing its expiration date with the current time.
     *
     * @param token JWT to check
     * @return {@code true} if the token has expired
     */
    private boolean isTokenExpired(String token) {
        return getExpiration(token).before(new Date());
    }
}
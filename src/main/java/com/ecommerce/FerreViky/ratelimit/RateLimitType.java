package com.ecommerce.FerreViky.ratelimit;

/**
 * Tipos de límite de peticiones. Cada tipo tiene su propio bucket por cliente,
 * así que consumir uno (p. ej. login) no afecta a los demás (p. ej. catálogo).
 * La capacidad es el número de peticiones permitidas por minuto.
 */
public enum RateLimitType {

    AUTH(10),     // /auth/** : login y registro (fuerza bruta)
    UPLOAD(10),   // subida de imágenes
    PUBLIC(45),  // /products/** : catálogo público
    USER(30),     // /cart, /customer/** : rutas autenticadas normales
    ADMIN(60);   // /admin/**

    private final int capacity;

    RateLimitType(int capacity) {
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }
}
package com.ecommerce.FerreViky.cache.cacheEvict;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Caching(evict = {
        @CacheEvict(value = "topVistas", allEntries = true),
        @CacheEvict(value = "topCalificados", allEntries = true),
        @CacheEvict(value = "topResenados", allEntries = true)
})
public @interface EvictTopProductos {
}
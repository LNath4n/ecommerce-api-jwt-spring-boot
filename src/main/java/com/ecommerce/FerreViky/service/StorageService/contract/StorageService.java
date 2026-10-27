package com.ecommerce.FerreViky.service.StorageService.contract;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

public interface StorageService {
    enum Carpeta {
        PRODUCTOS("images/"), PERFILES("profiles/");
        private final String prefijo;
        Carpeta(String p) { this.prefijo = p; }
        public String prefijo() { return prefijo; }
    }

    String upload(MultipartFile file, Carpeta carpeta) throws IOException;
    String getPublicUrl(String key);
    String getPresignedUrl(String key, Duration validez);
    void deleteAfterCommit(String key);
}
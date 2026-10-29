package com.ecommerce.FerreViky.service.storageService.implementations;

import com.ecommerce.FerreViky.service.storageService.contract.AbstractStorageService;
import com.ecommerce.FerreViky.utils.ImagenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@Profile("dev")
public class LocalStorageService extends AbstractStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private final Path root;
    private final String baseUrl;

    public LocalStorageService(ImagenValidator v,
                               @Value("${app.storage.local-dir}") String dir,
                               @Value("${app.storage.base-url}") String baseUrl) {
        super(v);
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
    }

    @Override
    protected void guardar(String key, MultipartFile file, Carpeta carpeta) throws IOException {
        Path destino = resolver(key);
        Files.createDirectories(destino.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // En dev no hay presigned: ambas carpetas se sirven por /uploads/**
    @Override public String getPublicUrl(String key) {
        return (key == null || key.isBlank()) ? null : baseUrl + "/uploads/" + key;
    }
    @Override public String getPresignedUrl(String key, Duration validez) { return getPublicUrl(key); }

    @Override
    protected void borrarSeguro(String key) {
        try {
            Files.deleteIfExists(resolver(key));
        } catch (Exception e) {
            log.warn("No se pudo borrar {} del disco: {}", key, e.getMessage());
        }
    }

    private Path resolver(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Key inválida");
        return p;
    }
}


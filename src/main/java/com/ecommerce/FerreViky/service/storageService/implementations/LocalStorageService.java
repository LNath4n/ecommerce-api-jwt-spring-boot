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

    public LocalStorageService(ImagenValidator validator,
                               @Value("${app.storage.local-dir}") String dir,
                               @Value("${app.storage.base-url}") String baseUrl) {
        super(validator);
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
    }

    @Override
    protected void storeObject(String key, byte[] data, String extension, StorageFolder folder) throws IOException {
        Path destination = resolvePath(key);
        Files.createDirectories(destination.getParent());
        Files.write(destination, data); // Overwrites the file if it exists. / Sobrescribe el archivo si existe.
    }

    // Dev does not use presigned URLs; both folders are served from /uploads/**. / En dev no hay URL prefirmada; ambas carpetas se sirven desde /uploads/**.
    @Override public String getPublicUrl(String key) {
        return (key == null || key.isBlank()) ? null : baseUrl + "/uploads/" + key;
    }
    @Override public String getPresignedUrl(String key, Duration validity) { return getPublicUrl(key); }

    @Override
    protected void deleteSafely(String key) {
        try {
            Files.deleteIfExists(resolvePath(key));
        } catch (Exception e) {
            log.warn("Could not delete {} from disk: {}", key, e.getMessage());
        }
    }

    private Path resolvePath(String key) {
        Path resolvedPath = root.resolve(key).normalize();
        if (!resolvedPath.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return resolvedPath;
    }
}


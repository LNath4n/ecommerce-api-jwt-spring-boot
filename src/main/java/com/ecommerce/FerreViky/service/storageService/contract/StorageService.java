package com.ecommerce.FerreViky.service.storageService.contract;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public interface StorageService {
    enum StorageFolder {
        PRODUCTS("images/"), PROFILES("profiles/");
        private final String prefix;
        StorageFolder(String prefix) { this.prefix = prefix; }
        public String prefix() { return prefix; }
    }

    String upload(MultipartFile file, StorageFolder folder, Map<String, String> metadata) throws IOException;

    // Mantiene compatible jijij
    default String upload(MultipartFile file, StorageFolder folder) throws IOException {
        return upload(file, folder, Map.of());
    }
    String getPublicUrl(String key);
    String getPresignedUrl(String key, Duration validity);
    void deleteAfterCommit(String key);
}
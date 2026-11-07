package com.ecommerce.FerreViky.service.storageService.contract;

import com.ecommerce.FerreViky.utils.ImagenValidator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public abstract class AbstractStorageService implements StorageService {
    private final ImagenValidator validator;
    protected AbstractStorageService(ImagenValidator validator) { this.validator = validator; }

    @Override
    public String upload(MultipartFile file, StorageFolder folder) throws IOException {
        ImagenValidator.ImagenLimpia sanitizedImage = validator.validarYSanitizar(file);
        String key = folder.prefix() + UUID.randomUUID() + sanitizedImage.ext();
        storeObject(key, sanitizedImage.data(), sanitizedImage.ext(), folder);
        deleteOnRollback(key);
        return key;
    }


    @Override
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deleteSafely(key); }
            });
        } else {
            deleteSafely(key);
        }
    }

    private void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteSafely(key);
            }
        });
    }

    protected abstract void storeObject(String key, byte[] data, String extension, StorageFolder folder) throws IOException;
    protected abstract void deleteSafely(String key); // Never throws. / Nunca lanza una excepción.
}
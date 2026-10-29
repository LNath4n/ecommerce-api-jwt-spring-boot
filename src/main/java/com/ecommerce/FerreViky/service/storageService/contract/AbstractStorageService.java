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
    public String upload(MultipartFile file, Carpeta carpeta) throws IOException {
        String ext = validator.validar(file);
        String key = carpeta.prefijo() + UUID.randomUUID() + ext;
        guardar(key, file, carpeta);
        borrarSiHaceRollback(key);
        return key;
    }

    @Override
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { borrarSeguro(key); }
            });
        } else {
            borrarSeguro(key);
        }
    }

    private void borrarSiHaceRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) borrarSeguro(key);
            }
        });
    }

    protected abstract void guardar(String key, MultipartFile file, Carpeta carpeta) throws IOException;
    protected abstract void borrarSeguro(String key); // nunca lanza excepción
}
package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ImagenInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageService {

    public enum Carpeta {
        PRODUCTOS("images/"),   // pública (lectura abierta en la política del bucket)
        PERFILES("profiles/");  // privada (se lee solo con URL prefirmada)

        private final String prefijo;
        Carpeta(String prefijo) { this.prefijo = prefijo; }
        public String prefijo() { return prefijo; }
    }

    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png"
    );

    // Nombre de formato que ImageIO reporta (en minúsculas) para cada Content-Type
    private static final Map<String, String> FORMATO_POR_TIPO = Map.of(
            "image/jpeg", "jpeg",
            "image/png",  "png"
    );

    private static final int MAX_LADO_PX = 4000;
    private static final long MAX_PIXELES = 16_000_000L; // ~16 MP

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public StorageService(S3Client s3, S3Presigner presigner,
                          @Value("${app.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    /**
     * Valida y sube una imagen al bucket.
     * <p>
     * Si se llama dentro de una transacción y esta termina en rollback, la imagen
     * recién subida se borra automáticamente para no dejarla huérfana.
     *
     * @param file    imagen a subir (solo JPG o PNG)
     * @param carpeta carpeta destino dentro del bucket
     * @return key generada del objeto en S3
     * @throws ImagenInvalidaException si el archivo no es una imagen válida
     * @throws IOException             si falla la lectura del archivo
     */
    public String upload(MultipartFile file, Carpeta carpeta) throws IOException {
        String ct = file.getContentType();
        String ext = (ct == null) ? null : TIPOS_PERMITIDOS.get(ct);
        if (ext == null) {
            throw new ImagenInvalidaException("Formato no permitido. Usa JPG o PNG");
        }

        validarImagen(file, ct);

        String key = carpeta.prefijo() + UUID.randomUUID() + ext;
        String cache = (carpeta == Carpeta.PRODUCTOS)
                ? "public,max-age=31536000,immutable"
                : "private,max-age=3600";

        try (InputStream in = file.getInputStream()) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket).key(key)
                            .contentType(ct)
                            .cacheControl(cache)
                            .build(),
                    RequestBody.fromInputStream(in, file.getSize()));
        }

        borrarSiHaceRollback(key);
        return key;
    }

    /**
     * Valida que el archivo sea realmente una imagen del tipo declarado,
     * que sus dimensiones sean razonables y que no esté corrupta.
     * Cualquier fallo se convierte en ImagenInvalidaException.
     */
    private void validarImagen(MultipartFile file, String contentType) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.getInputStream())) {
            if (iis == null) {
                throw new ImagenInvalidaException("El archivo no es una imagen válida");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new ImagenInvalidaException("El archivo no es una imagen válida");
            }

            ImageReader reader = readers.next();
            try {
                // El formato real debe coincidir con el Content-Type declarado
                String formatoReal = reader.getFormatName().toLowerCase();
                if (!formatoReal.equals(FORMATO_POR_TIPO.get(contentType))) {
                    throw new ImagenInvalidaException(
                            "El contenido del archivo no coincide con su formato");
                }

                reader.setInput(iis, true, true);

                // Solo lee el header: todavía no decodifica la imagen
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0
                        || w > MAX_LADO_PX || h > MAX_LADO_PX
                        || (long) w * h > MAX_PIXELES) {
                    throw new ImagenInvalidaException(
                            "La imagen es demasiado grande (máx. "
                                    + MAX_LADO_PX + "x" + MAX_LADO_PX + " px)");
                }

                // Ya es seguro decodificar: confirma que no está corrupta
                reader.read(0);

            } catch (ImagenInvalidaException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new ImagenInvalidaException("El archivo no es una imagen válida");
            } finally {
                reader.dispose();
            }
        }
    }

    /** Para la carpeta pública (images/). */
    public String getPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;
        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
    }

    /** Para la carpeta privada (profiles/): URL temporal de lectura. */
    public String getPresignedUrl(String key, Duration validez) {
        if (key == null || key.isBlank()) return null;
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(validez)
                .getObjectRequest(b -> b.bucket(bucket).key(key))
                .build();
        return presigner.presignGetObject(req).url().toExternalForm();
    }

    /**
     * Borra un objeto del bucket solo si la transacción actual confirma (commit).
     * Fuera de una transacción lo borra de inmediato.
     * Nunca lanza excepción: si falla, solo lo registra en el log.
     */
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrarSeguro(key);
                }
            });
        } else {
            borrarSeguro(key);
        }
    }

    /**
     * Si la transacción actual termina en rollback, borra de S3 el archivo recién
     * subido para no dejarlo huérfano. Fuera de una transacción no hace nada.
     * <p>
     * Se usa {@code STATUS_ROLLED_BACK} y no {@code != STATUS_COMMITTED} a propósito:
     * con {@code STATUS_UNKNOWN} no se sabe si la BD guardó la key, y borrar el archivo
     * podría dejar un registro apuntando a algo que ya no existe.
     */
    private void borrarSiHaceRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    borrarSeguro(key);
                }
            }
        });
    }

    /** Borra del bucket sin propagar errores (solo los registra). */
    private void borrarSeguro(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("No se pudo borrar {} de S3: {}", key, e.getMessage());
        }
    }
}
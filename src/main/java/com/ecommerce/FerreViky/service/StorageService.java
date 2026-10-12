package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ImagenInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.IOException;

@Service
public class StorageService {
    private final S3Client s3;
    private final String bucket;

    public StorageService(S3Client s3, @Value("${app.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png"
    );

    public String upload(MultipartFile file) throws IOException {
        // Lista blanca: el tipo debe estar permitido y de ahí sale la extensión
        String ct = file.getContentType();
        String ext = (ct == null) ? null : TIPOS_PERMITIDOS.get(ct);
        if (ext == null) {
            throw new ImagenInvalidaException("Formato no permitido. Usa JPG o PNG");
        }

        // Verificar que el contenido real sea una imagen
        try (InputStream in = file.getInputStream()) {
            if (ImageIO.read(in) == null) {
                throw new ImagenInvalidaException("El archivo no es una imagen válida");
            }
        }

        String key = "images/" + UUID.randomUUID() + ext;

        // Se abre un stream NUEVO, porque el anterior ya se consumió al leerlo
        try (InputStream in = file.getInputStream()) {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(ct).build(),
                    RequestBody.fromInputStream(in, file.getSize()));
        }
        return key;
    }

    public String getPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;
        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
package com.ecommerce.FerreViky.service.StorageService.implementations;

import com.ecommerce.FerreViky.utils.AbstractStorageService;
import com.ecommerce.FerreViky.utils.ImagenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Service
@Profile("prod")
public class S3StorageService extends AbstractStorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageService(ImagenValidator v, S3Client s3, S3Presigner presigner,
                            @Value("${app.s3.bucket}") String bucket) {
        super(v);
        this.s3 = s3; this.presigner = presigner; this.bucket = bucket;
    }

    @Override
    protected void guardar(String key, MultipartFile file, Carpeta carpeta) throws IOException {
        String cache = (carpeta == Carpeta.PRODUCTOS)
                ? "public,max-age=31536000,immutable" : "private,max-age=3600";
        try (InputStream in = file.getInputStream()) {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key)
                            .contentType(file.getContentType()).cacheControl(cache).build(),
                    RequestBody.fromInputStream(in, file.getSize()));
        }
    }

    @Override
    public String getPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;
        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
    }

    @Override
    public String getPresignedUrl(String key, Duration validez) {
        if (key == null || key.isBlank()) return null;
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(validez)
                        .getObjectRequest(b -> b.bucket(bucket).key(key)).build())
                .url().toExternalForm();
    }

    @Override
    protected void borrarSeguro(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("No se pudo borrar {} de S3: {}", key, e.getMessage());
        }
    }
}
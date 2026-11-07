package com.ecommerce.FerreViky.service.storageService.implementations;

import com.ecommerce.FerreViky.service.storageService.contract.AbstractStorageService;
import com.ecommerce.FerreViky.utils.ImageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;

@Service
@Profile("prod")
public class S3StorageService extends AbstractStorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageService(ImageValidator validator, S3Client s3, S3Presigner presigner,
                            @Value("${app.s3.bucket}") String bucket) {
        super(validator);
        this.s3 = s3; this.presigner = presigner; this.bucket = bucket;
    }

    @Override
    protected void storeObject(String key, byte[] data, String extension, StorageFolder folder) throws IOException {
        String cacheControl = (folder == StorageFolder.PRODUCTS)
                ? "public,max-age=31536000,immutable" : "private,max-age=3600";
        String contentType = extension.equals(".png") ? "image/png" : "image/jpeg";

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType(contentType).cacheControl(cacheControl).build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public String getPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;
        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
    }

    @Override
    public String getPresignedUrl(String key, Duration validity) {
        if (key == null || key.isBlank()) return null;
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(validity)
                        .getObjectRequest(b -> b.bucket(bucket).key(key)).build())
                .url().toExternalForm();
    }

    @Override
    protected void deleteSafely(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("Could not delete {} from S3: {}", key, e.getMessage());
        }
    }
}
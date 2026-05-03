package com.trimlink.common.storage;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around MinIO SDK.
 * Handles bucket creation, file upload, deletion, and public URL generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageProperties props;
    private MinioClient client;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            client = MinioClient.builder()
                    .endpoint(props.getEndpoint())
                    .credentials(props.getAccessKey(), props.getSecretKey())
                    .build();

            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(props.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(props.getBucket()).build());
                // Set bucket to public-read so images are directly accessible via URL
                String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }""".formatted(props.getBucket());
                client.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(props.getBucket())
                        .config(policy)
                        .build());
                log.info("Created MinIO bucket '{}' with public-read policy", props.getBucket());
            } else {
                log.info("MinIO bucket '{}' already exists", props.getBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO initialization failed (storage unavailable): {}", e.getMessage());
        }
    }

    /**
     * Upload a multipart file to MinIO.
     * @param file     the uploaded file
     * @param folder   logical folder prefix (e.g. "shops", "avatars", "services")
     * @return public URL of the stored object
     */
    public String upload(MultipartFile file, String folder) {
        if (client == null) throw new IllegalStateException("Storage service is not available");

        String ext = getExtension(file.getOriginalFilename());
        String objectName = folder + "/" + UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return props.getPublicUrl() + "/" + props.getBucket() + "/" + objectName;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file by its public URL.
     */
    public void delete(String publicUrl) {
        if (client == null || publicUrl == null) return;
        try {
            // Extract object name from URL: everything after bucket name
            String prefix = props.getPublicUrl() + "/" + props.getBucket() + "/";
            if (publicUrl.startsWith(prefix)) {
                String objectName = publicUrl.substring(prefix.length());
                client.removeObject(RemoveObjectArgs.builder()
                        .bucket(props.getBucket())
                        .object(objectName)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to delete object from MinIO: {}", e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}

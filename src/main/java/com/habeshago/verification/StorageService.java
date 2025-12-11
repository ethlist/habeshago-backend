package com.habeshago.verification;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final String bucketName;
    private final Storage storage;
    private final boolean devMode;

    public StorageService(
            @Value("${habeshago.gcs.bucket-name:}") String bucketName,
            @Value("${habeshago.gcs.project-id:}") String projectId) {
        this.bucketName = bucketName;
        this.devMode = bucketName == null || bucketName.isBlank();

        if (devMode) {
            log.warn("GCS not configured - running in DEV MODE. Files will not be uploaded.");
            this.storage = null;
        } else {
            this.storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
        }
    }

    /**
     * Upload a file to GCS and return the public URL.
     * In dev mode, returns a mock URL.
     */
    public String uploadFile(MultipartFile file, String folder, Long userId) throws IOException {
        String filename = generateFilename(file.getOriginalFilename(), userId);
        String objectName = folder + "/" + filename;

        if (devMode) {
            log.info("DEV MODE - Would upload file: {} to {}", filename, objectName);
            return "https://storage.example.com/" + bucketName + "/" + objectName;
        }

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        storage.create(blobInfo, file.getBytes());

        String publicUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
        log.info("Uploaded file {} to GCS: {}", filename, publicUrl);

        return publicUrl;
    }

    /**
     * Delete a file from GCS.
     */
    public void deleteFile(String url) {
        if (devMode || url == null) {
            return;
        }

        try {
            // Extract object name from URL
            String prefix = String.format("https://storage.googleapis.com/%s/", bucketName);
            if (url.startsWith(prefix)) {
                String objectName = url.substring(prefix.length());
                BlobId blobId = BlobId.of(bucketName, objectName);
                storage.delete(blobId);
                log.info("Deleted file from GCS: {}", objectName);
            }
        } catch (Exception e) {
            log.error("Failed to delete file from GCS: {}", url, e);
        }
    }

    private String generateFilename(String originalFilename, Long userId) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
    }
}

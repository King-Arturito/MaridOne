package org.example.maridone.document.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file, String username, String targetDir);

    Resource load(String s3Key);

    String generatePresignedUrl(String s3Key);

    void delete(String s3Key);
}
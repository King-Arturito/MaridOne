package org.example.maridone.document.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file, String username);

    Resource load(String filePath);

    void delete(String filePath);

    String generateDownloadUrl(String filePath);
}

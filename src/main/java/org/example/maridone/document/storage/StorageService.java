package org.example.maridone.document.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file, String username, String targetDir);

    Resource load(String filePath);

    default Resource loadLibraryDocument(String subDir, String fileName) {
        throw new UnsupportedOperationException("Library documents are not supported by this storage.");
    }

    default String probeContentType(String subDir, String fileName) {
        return "application/octet-stream";
    }

    void delete(String filePath);

    String generateDownloadUrl(String filePath);
}

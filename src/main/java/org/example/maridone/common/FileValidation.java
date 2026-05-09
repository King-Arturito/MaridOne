package org.example.maridone.common;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class FileValidation {

    public static boolean exceedsFileSizeLimit(List<MultipartFile> files, long maxBytes) {
        if (files == null || files.isEmpty()) return false;
        return files.stream()
                .anyMatch(file -> file != null && file.getSize() > maxBytes);
    }

    public static boolean exceedsFileUploadLimit(long numOfFiles, long maxFileUpload) {
        return numOfFiles > maxFileUpload;
    }
}

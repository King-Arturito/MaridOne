package org.example.maridone.document.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public final class DocumentLibraryDirectories {

    private static final Set<String> ALLOWED_DIRECTORIES = Set.of(
            "employee-handbook",
            "training-materials",
            "onboarding-materials"
    );

    private DocumentLibraryDirectories() {}

    public static void validate(String subDir) {
        if (!ALLOWED_DIRECTORIES.contains(subDir)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown document library directory: " + subDir
            );
        }
    }
}

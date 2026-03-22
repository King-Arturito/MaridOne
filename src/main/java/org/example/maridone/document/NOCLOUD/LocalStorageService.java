package org.example.maridone.document.NOCLOUD;

import org.example.maridone.document.storage.StorageService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

//todo:  REMOVE AFTER DEFENSE
@Service
@Profile("dev")
public class LocalStorageService implements StorageService {

    private final Path rootLocation = Paths.get("local-documents");

    public LocalStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage directory", e);
        }
    }

    @Override
    public String store(MultipartFile file, String username) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }

            // Generate a unique filename so files with the same name don't overwrite each other
            String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // Group files by username in sub-folders
            Path userDir = rootLocation.resolve(username);
            Files.createDirectories(userDir);

            Path destinationFile = userDir.resolve(Paths.get(uniqueFileName))
                    .normalize().toAbsolutePath();

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            return destinationFile.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file locally.", e);
        }
    }

    @Override
    public Resource load(String filePath) {
        try {
            Path file = Paths.get(filePath);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read local file: " + filePath);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read local file: " + filePath, e);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Path file = Paths.get(filePath);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete local file: " + filePath, e);
        }
    }

    @Override
    public String generateDownloadUrl(String filePath) {
        return "http://localhost:8080/api/LOCAL/document/file?path=" + filePath;
    }
}
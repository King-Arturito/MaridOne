package org.example.maridone.document.NOCLOUD;

import org.example.maridone.document.storage.StorageService;
import org.example.maridone.document.storage.DocumentLibraryDirectories;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

//todo:  REMOVE AFTER DEFENSE
@Service
@Profile("!prod")
public class LocalStorageService implements StorageService {

    private final Path rootLocation = Paths.get("local-documents").toAbsolutePath().normalize();

    public LocalStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage directory", e);
        }
    }

    @Override
    public String store(MultipartFile file, String username, String dirName) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }

            String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // local-document/username/dirName/
            Path userDir = rootLocation.resolve(username).resolve(dirName);
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
    public Resource loadLibraryDocument(String subDir, String fileName) {
        DocumentLibraryDirectories.validate(subDir);

        try {
            Path directory = rootLocation.resolve(subDir).normalize();
            Path file = directory.resolve(fileName).normalize();

            if (!file.startsWith(directory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document path");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not read local library file: " + fileName);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not read local library file: " + fileName, e);
        }
    }

    @Override
    public String probeContentType(String subDir, String fileName) {
        try {
            Path file = rootLocation.resolve(subDir).resolve(fileName).normalize();
            String contentType = Files.probeContentType(file);
            return contentType != null ? contentType : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
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

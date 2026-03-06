package org.example.maridone.document;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.DefaultProperties;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.storage.StorageService;
import org.example.maridone.exception.AccountNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final StorageService cloudStorageService;
    private final DocumentPathRepository documentPathRepository;
    private final UserAccountRepository userAccountRepository;

    public DocumentController(StorageService cloudStorageService,
                              DocumentPathRepository documentPathRepository,
                              UserAccountRepository userAccountRepository) {
        this.cloudStorageService = cloudStorageService;
        this.documentPathRepository = documentPathRepository;
        this.userAccountRepository = userAccountRepository;
    }

    //view a document
    @GetMapping("/{pathId}/view")
    @ExecutionTime
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<Resource> view(@PathVariable Long pathId) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Resource file = cloudStorageService.load(document.getFilePath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .body(file);
    }

    //upload document
    @PostMapping("/upload")
    @ExecutionTime
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentPath> upload(@RequestParam("file") MultipartFile file,
                                               Authentication authentication) {

        String username = authentication.getName();

        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        String savedFilePath = cloudStorageService.store(file, username);

        DocumentPath doc = new DocumentPath();
        doc.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");
        doc.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        doc.setFilePath(savedFilePath);
        doc.setUsername(user);

        DocumentPath savedDoc = documentPathRepository.save(doc);

        return ResponseEntity.ok(savedDoc);
    }

    //replace a document
    @PutMapping("/{id}/replace")
    @ExecutionTime
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<DocumentPath> replace(@PathVariable("id") Long pathId,
                                                @RequestParam("file") MultipartFile file,
                                                Authentication authentication) {

        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Delete old file
        cloudStorageService.delete(document.getFilePath());

        // Upload the new file
        String newFilePath = cloudStorageService.store(file, authentication.getName());

        // Update database record
        document.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");
        document.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        document.setFilePath(newFilePath);

        DocumentPath updatedDoc = documentPathRepository.save(document);

        return ResponseEntity.ok(updatedDoc);
    }

    // delete document
    @DeleteMapping("/{id}/delete")
    @ExecutionTime
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<String> delete(@PathVariable("id") Long pathId) {

        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // delete from cloud
        cloudStorageService.delete(document.getFilePath());

        // Delete data from our db
        documentPathRepository.delete(document);

        return ResponseEntity.ok("Document deleted successfully");
    }
}
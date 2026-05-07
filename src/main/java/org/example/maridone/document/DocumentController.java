package org.example.maridone.document;

import org.example.maridone.auth.sensitive.SensitiveAccessService;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.enums.Directories;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;
    private final SensitiveAccessService sensitiveAccessService;

    public DocumentController(DocumentService documentService,
                              SensitiveAccessService sensitiveAccessService) {
        this.documentService = documentService;
        this.sensitiveAccessService = sensitiveAccessService;
    }

    @PostMapping("/public/upload")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public ResponseEntity<Void> uploadPublicDocument(MultipartFile file, Authentication authentication) {
        String uploadedBy = authentication.getName();
        documentService.uploadPublicDocument(file, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    //returns download link
    @GetMapping("/public/view/{pathId}")
    @PreAuthorize("isAuthenticated()")
    public String getPublicDocument(@PathVariable Long pathId) {
        return documentService.getPublicDocument(pathId);
    }

    @GetMapping("/public/download/{pathId}")
    @PreAuthorize("isAuthenticated()")
    public String downloadPublicDocument(@PathVariable Long pathId) {
        return documentService.downloadPublicDocument(pathId);
    }

    @GetMapping("/public/view/list")
    @PreAuthorize("isAuthenticated()")
    public List<DocumentResponseDto> getPublicDocuments() {
        return documentService.getPublicDocuments();
    }

    @DeleteMapping("/public/delete/{pathId}")
    @PreAuthorize("hasRole('MANAGEMENT')")
    public ResponseEntity<String> deletePublicDocument(@PathVariable("pathId") Long pathId) {
        documentService.deletePublicDocument(pathId);
        return ResponseEntity.ok("Document deleted successfully.");
    }

    //returns a list of an employee's list of documents
    //hr can see an employee's list of documents by providing their username
    @GetMapping("/private/view/list")
    @PreAuthorize("isAuthenticated()")
    public List<DocumentResponseDto> getPrivateDocuments(
            @RequestParam(required = false) String username,
            Authentication authentication) {
        return documentService.getPrivateDocuments(username, authentication);
    }

    @GetMapping("/private/view/entity")
    @PreAuthorize("isAuthenticated()")
    public String getPrivateDocumentByEntity(
            @RequestParam String connectedEntity,
            @RequestParam Long connectedId,
            Authentication authentication
    ) {
        sensitiveAccessService.assertSensitiveAccess(authentication);
        return documentService.getPrivateDocumentByEntity(connectedEntity, connectedId);
    }

    //returns a presigned url of that specific file obtained from getPrivateDocuments()
    @GetMapping("/private/view/{pathId}")
    @PreAuthorize("hasRole('HR') or @documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public String view(@PathVariable Long pathId, Authentication authentication) {
        sensitiveAccessService.assertSensitiveAccess(authentication);
        return documentService.getPrivateDocument(pathId);
    }

    @GetMapping("/private/download/{pathId}")
    @PreAuthorize("hasRole('HR') or @documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public String downloadPrivateDocument(@PathVariable Long pathId, Authentication authentication) {
        sensitiveAccessService.assertSensitiveAccess(authentication);
        return documentService.downloadPrivateDocument(pathId);
    }

    //most likely will be phased out
    @PostMapping("/private/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentResponseDto> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("subDir") Directories subDir,
                                                      @RequestParam("id") Long connectedId,
                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadPrivateDocument(file, authentication.getName(), subDir, connectedId));
    }

    //most likely will be used as default
    @PostMapping("/private/upload/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("subDir") Directories subDir,
            @RequestParam(value = "connectedId", required = false) Long connectedId,
            Authentication authentication) {
        documentService.batchUploadPrivateDocuments(files, authentication.getName(), subDir, List.of(connectedId));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/private/delete/{id}")
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<String> delete(@PathVariable("id") Long pathId) {
        documentService.deletePrivateDocument(pathId);
        return ResponseEntity.ok("Document deleted successfully");
    }
}

package org.example.maridone.document;

import org.example.maridone.document.path.DocumentPath;
import org.springframework.context.annotation.Profile;
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
@Profile("prod")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{pathId}/view")
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<Resource> view(@PathVariable Long pathId) {
        DocumentViewResult result = documentService.view(pathId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.fileType()))
                .body(result.file());
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentPath> upload(@RequestParam("file") MultipartFile file,
                                               Authentication authentication) {
        return ResponseEntity.ok(documentService.upload(file, authentication.getName()));
    }

    @PutMapping("/{id}/replace")
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<DocumentPath> replace(@PathVariable("id") Long pathId,
                                                @RequestParam("file") MultipartFile file,
                                                Authentication authentication) {
        return ResponseEntity.ok(documentService.replace(pathId, file, authentication.getName()));
    }

    @DeleteMapping("/{id}/delete")
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<String> delete(@PathVariable("id") Long pathId) {
        documentService.delete(pathId);
        return ResponseEntity.ok("Document deleted successfully");
    }
}
package org.example.maridone.document.NOCLOUD;

import org.example.maridone.document.DocumentViewResult;
import org.example.maridone.document.DocumentService;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/LOCAL/document")
@Profile("!prod")
public class LocalDocumentController {

    private final DocumentService documentService;

    public LocalDocumentController(DocumentService documentService) {
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

    @GetMapping("/library/{subDir}/{fileName:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> viewLibraryDocument(@PathVariable String subDir,
                                                        @PathVariable String fileName) {
        DocumentViewResult result = documentService.viewLibraryDocument(subDir, fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.fileType()))
                .body(result.file());
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentResponseDto> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("subDir") String subDir,
                                                      Authentication authentication) {
        return ResponseEntity.ok(documentService.upload(file, authentication.getName(), subDir));
    }


    @PutMapping("/{id}/replace")
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<DocumentResponseDto> replace(@PathVariable("id") Long pathId,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam("subDir") String subDir,
                                                       Authentication authentication) {
        return ResponseEntity.ok(documentService.replace(pathId, file, authentication.getName(), subDir));
    }

    @DeleteMapping("/{id}/delete")
    @PreAuthorize("@documentOwnerCheck.isSelf(#pathId, authentication.name)")
    public ResponseEntity<String> delete(@PathVariable("id") Long pathId) {
        documentService.delete(pathId);
        return ResponseEntity.ok("Document deleted successfully");
    }
}

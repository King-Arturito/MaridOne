package org.example.maridone.document;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.storage.CloudStorageService;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("prod")
public class DocumentService {

    private final CloudStorageService cloudStorageService;
    private final DocumentPathRepository documentPathRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentMapper documentMapper;

    public DocumentService(CloudStorageService cloudStorageService,
                           DocumentPathRepository documentPathRepository,
                           UserAccountRepository userAccountRepository,
                           DocumentMapper documentMapper) {
        this.cloudStorageService = cloudStorageService;
        this.documentPathRepository = documentPathRepository;
        this.userAccountRepository = userAccountRepository;
        this.documentMapper = documentMapper;
    }

    @ExecutionTime
    public DocumentViewResult view(Long pathId) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Resource file = cloudStorageService.load(document.getFilePath());
        return new DocumentViewResult(file, document.getFileName(), document.getFileType());
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public DocumentResponseDto upload(MultipartFile file, String username) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        String savedFilePath = cloudStorageService.store(file, username);

        DocumentPath doc = new DocumentPath();
        doc.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");
        doc.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        doc.setFilePath(savedFilePath);
        doc.setUsername(user);
        documentPathRepository.save(doc);

        return documentMapper.toDocumentResponseDto(doc);
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public DocumentResponseDto replace(Long pathId, MultipartFile file, String username) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        cloudStorageService.delete(document.getFilePath());

        String newFilePath = cloudStorageService.store(file, username);

        document.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");
        document.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        document.setFilePath(newFilePath);

        documentPathRepository.save(document);
        return documentMapper.toDocumentResponseDto(document);
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public void delete(Long pathId) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        cloudStorageService.delete(document.getFilePath());
        documentPathRepository.delete(document);
    }
}
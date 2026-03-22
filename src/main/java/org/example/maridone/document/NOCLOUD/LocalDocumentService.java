package org.example.maridone.document.NOCLOUD;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.DocumentPathRepository;
import org.example.maridone.document.DocumentViewResult;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("dev")
public class LocalDocumentService {

    private final LocalStorageService localStorageService;
    private final DocumentPathRepository documentPathRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentMapper documentMapper;

    public LocalDocumentService(LocalStorageService localStorageService,
                           DocumentPathRepository documentPathRepository,
                           UserAccountRepository userAccountRepository,
                                DocumentMapper documentMapper) {
        this.localStorageService = localStorageService;
        this.documentPathRepository = documentPathRepository;
        this.userAccountRepository = userAccountRepository;
        this.documentMapper = documentMapper;
    }

    @ExecutionTime
    public DocumentViewResult view(Long pathId) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Resource file = localStorageService.load(document.getFilePath());
        return new DocumentViewResult(file, document.getFileName(), document.getFileType());
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public DocumentResponseDto upload(MultipartFile file, String username) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        String savedFilePath = localStorageService.store(file, username);

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

        localStorageService.delete(document.getFilePath());

        String newFilePath = localStorageService.store(file, username);

        document.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");
        document.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        document.setFilePath(newFilePath);

        documentPathRepository.save(document);

        return documentMapper.toDocumentResponseDto(document);
    }

    @ExecutionTime
    @AuditLog
    @Transactional
    public void delete(Long pathId) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        localStorageService.delete(document.getFilePath());
        documentPathRepository.delete(document);
    }
}
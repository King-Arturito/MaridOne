package org.example.maridone.document;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.storage.StorageService;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.example.maridone.payroll.payslip.FileUploadBatchRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final StorageService storageService;
    private final DocumentPathRepository documentPathRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentMapper documentMapper;

    public DocumentService(StorageService storageService,
                           DocumentPathRepository documentPathRepository,
                           UserAccountRepository userAccountRepository,
                           DocumentMapper documentMapper) {
        this.storageService = storageService;
        this.documentPathRepository = documentPathRepository;
        this.userAccountRepository = userAccountRepository;
        this.documentMapper = documentMapper;
    }

    @ExecutionTime
    public DocumentViewResult view(Long pathId) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Resource file = storageService.load(document.getFilePath());
        return new DocumentViewResult(file, document.getFileName(), document.getFileType());
    }

    @ExecutionTime
    public DocumentViewResult viewLibraryDocument(String subDir, String fileName) {
        Resource file = storageService.loadLibraryDocument(subDir, fileName);
        String contentType = storageService.probeContentType(subDir, fileName);
        return new DocumentViewResult(file, fileName, contentType);
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public DocumentResponseDto upload(MultipartFile file, String username, String subDir) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        String savedFilePath = storageService.store(file, username, subDir);

        DocumentPath doc = buildDocumentPath(file, user, subDir, savedFilePath);
        documentPathRepository.save(doc);

        return documentMapper.toDocumentResponseDto(doc);
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public void batchUpload(List<FileUploadBatchRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        List<String> usernames = requests.stream()
                .map(FileUploadBatchRequest::username)
                .toList();

        Map<String, UserAccount> userMap = userAccountRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(UserAccount::getUsername, user -> user));

        List<DocumentPath> documents = new ArrayList<>();

        for (FileUploadBatchRequest request : requests) {
            String username = request.username();
            UserAccount user = userMap.get(username);

            if (user == null) {
                throw new AccountNotFoundException("User not found: " + username);
            }

            String savedFilePath = storageService.store(request.file(), username, request.subDir());
            documents.add(buildDocumentPath(request.file(), user, request.subDir(), savedFilePath));
        }

        documentPathRepository.saveAll(documents);
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    public DocumentResponseDto replace(Long pathId, MultipartFile file, String username, String subDir) {
        DocumentPath document = documentPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        storageService.delete(document.getFilePath());

        String newFilePath = storageService.store(file, username, subDir);

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

        storageService.delete(document.getFilePath());
        documentPathRepository.delete(document);
    }

    private DocumentPath buildDocumentPath(MultipartFile file, UserAccount user, String subDir, String savedFilePath) {
        DocumentPath documentPath = new DocumentPath();
        documentPath.setFileName(resolveStoredFileName(file, subDir));
        documentPath.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        documentPath.setFilePath(savedFilePath);
        documentPath.setUserAccount(user);
        return documentPath;
    }

    private String resolveStoredFileName(MultipartFile file, String subDir) {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        return subDir + "_" + LocalDate.now() + extension;
    }
}

package org.example.maridone.document;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.common.FileValidation;
import org.example.maridone.config.CloudConfig;
import org.example.maridone.config.DocumentConfig;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.event.S3CleanupEvent;
import org.example.maridone.document.event.S3RollbackEvent;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.path.DocumentPathLink;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.storage.CloudStorageService;
import org.example.maridone.enums.Directories;
import org.example.maridone.exception.badrequest.MaxFileUploadExceededException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.DocumentNotFound;
import org.example.maridone.exception.notfound.PathNotFoundException;
import org.example.maridone.exception.toolarge.FileTooLargeException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.exception.unauthorized.UnauthorizedAccessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.example.maridone.payroll.payslip.FileUploadBatchRequest;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final CloudStorageService cloudStorageService;
    private final CloudConfig cloudConfig;
    private final DocumentConfig documentConfig;
    private final DocumentPathRepository documentPathRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentMapper documentMapper;

    public DocumentService(CloudStorageService cloudStorageService,
                           ApplicationEventPublisher applicationEventPublisher,
                           CloudConfig cloudConfig,
                           DocumentConfig documentConfig,
                           DocumentPathRepository documentPathRepository,
                           UserAccountRepository userAccountRepository,
                           DocumentMapper documentMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.cloudStorageService = cloudStorageService;
        this.cloudConfig = cloudConfig;
        this.documentConfig = documentConfig;
        this.documentPathRepository = documentPathRepository;
        this.userAccountRepository = userAccountRepository;
        this.documentMapper = documentMapper;
    }

    //batchUpload for DIFFERENT USERS
    @ExecutionTime
    @Transactional
    @AuditLog
    public void batchUpload(List<FileUploadBatchRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<String> usernames = requests.stream()
                .map(FileUploadBatchRequest::username)
                .toList();

        Map<String, UserAccount> userMap = userAccountRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(UserAccount::getUsername, user -> user));


        List<DocumentPath> documents = new ArrayList<>();
        List<String> uploadedS3Keys = new ArrayList<>();

        for (FileUploadBatchRequest request : requests) {
            String username = request.username();
            UserAccount user = userMap.get(username);

            if (user == null) throw new AccountNotFoundException("UserAccount with: " + username + " not found.");

            String s3Key = cloudStorageService.store(request.file(), request.employeeId().toString(), request.subDir().getDisplayName());
            uploadedS3Keys.add(s3Key);

            DocumentPath dp = buildDocumentPath(request.file(), user, s3Key, true);

            DocumentPathLink link = new DocumentPathLink();
            link.setConnectedEntity(request.subDir().getDisplayName());
            link.setConnectedId(null);
            dp.getLinks().add(link);

            documents.add(dp);
        }

        //if dp record creation fails, do s3cleanup to avoid orphaned documents
        applicationEventPublisher.publishEvent(new S3RollbackEvent(uploadedS3Keys));
        documentPathRepository.saveAll(documents);
    }

    //batchUpload() FOR ONE USER
    @ExecutionTime
    @Transactional
    @AuditLog
    public void batchUploadPrivateDocuments(List<MultipartFile> files, String username, Directories subDir, List<Long> connectedIds) {
        if (FileValidation.exceedsFileUploadLimit(files.size(), documentConfig.getMaxFileUpload())) {
            throw new MaxFileUploadExceededException("files to upload exceeded. Limit: " + documentConfig.getMaxFileUpload() + " files");
        }
        if (FileValidation.exceedsFileSizeLimit(files, documentConfig.getMaxFileSize().toBytes()))  {
            throw new FileTooLargeException("One or more files exceeds file size limit of: " + documentConfig.getMaxFileSize() + " bytes");
        }

        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + username + " not found."));

        List<DocumentPath> documentPaths = new ArrayList<>();
        List<String> uploadedS3Keys = new ArrayList<>();

        for (MultipartFile file : files) {
            String s3Key = cloudStorageService.store(file, userAccount.getEmployeeId().toString(), subDir.getDisplayName());
            uploadedS3Keys.add(s3Key);

            DocumentPath dp = buildDocumentPath(file, userAccount, s3Key, false);
            if (connectedIds != null && !connectedIds.isEmpty()) {
                String entityType = subDir.getDisplayName();

                for (Long id : connectedIds) {
                    DocumentPathLink link = new DocumentPathLink();
                    link.setConnectedEntity(entityType);
                    link.setConnectedId(id);

                    dp.getLinks().add(link);
                }
            }
            documentPaths.add(dp);
        }
        applicationEventPublisher.publishEvent(new S3RollbackEvent(uploadedS3Keys));
        documentPathRepository.saveAll(documentPaths);
    }

    @ExecutionTime
    @Transactional
    public void uploadPublicDocument(MultipartFile file, String uploadedBy) {
        Long userId = userAccountRepository.findIdByUsername(uploadedBy)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + uploadedBy + " not found."));

        String s3Key = cloudStorageService.store(file, "MaridOne", Directories.PUBLIC.getDisplayName());

        UserAccount proxy = userAccountRepository.getReferenceById(userId);
        DocumentPath documentPath = buildDocumentPath(file, proxy, s3Key, true);

        applicationEventPublisher.publishEvent(new S3RollbackEvent(List.of(s3Key)));
        documentPathRepository.save(documentPath);
    }

    @ExecutionTime
    @Transactional
    public DocumentResponseDto uploadPrivateDocument(MultipartFile file, String name, Directories subDir, Long connectedId) {
        UserAccount userAccount = userAccountRepository.findByUsername(name)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + name + " not found."));

        String s3Key = cloudStorageService.store(file, userAccount.getEmployeeId().toString(), subDir.getDisplayName());

        DocumentPath dp = buildDocumentPath(file, userAccount, s3Key, false);

        DocumentPathLink link = new DocumentPathLink();
        link.setConnectedEntity(subDir.getDisplayName());
        link.setConnectedId(connectedId);
        dp.getLinks().add(link);

        applicationEventPublisher.publishEvent(new S3RollbackEvent(List.of(s3Key)));
        dp = documentPathRepository.save(dp);

        return new DocumentResponseDto(
                dp.getPathId(), dp.getFileName(), dp.getFileType(), dp.getFileSizeBytes(), dp.getCreatedAt(), dp.getUpdatedAt(), dp.getIsPublic());
    }

    public String getPublicDocument(Long pathId) {
        DocumentPath documentPath = documentPathRepository.findById(pathId).orElseThrow(
                () -> new PathNotFoundException(pathId));
        return cloudStorageService.generatePresignedUrl(documentPath.getS3Key());
    }

    public List<DocumentResponseDto> getPublicDocuments() {
        Specification<DocumentPath> spec = Specification.allOf(
                CommonSpecs.fieldEquals("isPublic", true)
        );
        List<DocumentPath> documentPaths = documentPathRepository.findAll(spec);
        return documentMapper.toDocumentResponseDtos(documentPaths);
    }

    public String downloadPublicDocument(Long pathId) {
        DocumentPath documentPath = documentPathRepository.findById(pathId).orElseThrow(
                () -> new PathNotFoundException(pathId));
        return cloudStorageService.downloadPresignedUrl(documentPath.getS3Key(), documentPath.getFileName());
    }

    public String getPrivateDocument(Long pathId) {
        DocumentPath documentPath = documentPathRepository.findById(pathId).orElseThrow(
                () -> new PathNotFoundException(pathId));
        return cloudStorageService.generatePresignedUrl(documentPath.getS3Key());
    }

    public String getPrivateDocumentByEntity(String connectedEntity, Long connectedId) {
        DocumentPath documentPath = documentPathRepository.findDocumentByEntityAndId(connectedEntity,connectedId)
                .orElseThrow(() -> new DocumentNotFound("Document not found for this connectedEntity and connectedId"));

        return cloudStorageService.generatePresignedUrl(documentPath.getS3Key());
    }

    public List<DocumentResponseDto> getPrivateDocuments(String username, Authentication authentication) {
        boolean isHr = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR"));
        List<DocumentResponseDto> documentPaths;

        // if username exists and isHr, finds all documents connected to username
        // else, get own documents
        if (username != null && isHr) {
            documentPaths = documentMapper.toDocumentResponseDtos(documentPathRepository.findAllByUsername(username));
        } else {
            documentPaths = documentMapper.toDocumentResponseDtos(documentPathRepository.findAllByUsername(authentication.getName()));
        }
        return documentPaths;
    }

    public String downloadPrivateDocument(Long pathId) {
        DocumentPath documentPath = documentPathRepository.findById(pathId).orElseThrow(
                () -> new PathNotFoundException(pathId));
        return cloudStorageService.downloadPresignedUrl(documentPath.getS3Key(), documentPath.getFileName());
    }

    @Transactional
    public void deletePublicDocument(Long pathId) {
        DocumentPath documentPath = documentPathRepository.findById(pathId).orElseThrow(
                () ->  new PathNotFoundException(pathId)
        );
        if (!Boolean.TRUE.equals(documentPath.getIsPublic())) {
            throw new InvalidActionException("this document is not public.");
        }
        documentPathRepository.delete(documentPath);
        applicationEventPublisher.publishEvent(new S3CleanupEvent(List.of(documentPath.getS3Key())));
    }

    @Transactional
    public void deletePrivateDocument(Long pathId) {
        DocumentPath documentPath = documentPathRepository.findById(pathId).orElseThrow(
                () ->  new PathNotFoundException(pathId)
        );
        documentPathRepository.delete(documentPath);
        applicationEventPublisher.publishEvent(new S3CleanupEvent(List.of(documentPath.getS3Key())));
    }

    //helper method for uploads
    protected DocumentPath buildDocumentPath(MultipartFile file,
                                             UserAccount userAccount,
                                             String s3Key,
                                             boolean isPublic) {
        DocumentPath documentPath = new DocumentPath();
        documentPath.setFileName(StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename())));
        documentPath.setFileType(file.getContentType());
        documentPath.setIsPublic(isPublic);
        documentPath.setUserAccount(userAccount);
        documentPath.setFileSizeBytes(file.getSize());
        documentPath.setS3Key(s3Key);
        return documentPath;
    }

}

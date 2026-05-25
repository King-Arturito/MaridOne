package org.example.maridone.document;

import org.example.maridone.config.CloudConfig;
import org.example.maridone.config.DocumentConfig;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.storage.CloudStorageService;
import org.example.maridone.enums.Directories;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.exception.toolarge.FileTooLargeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private CloudStorageService cloudStorageService;
    @Mock private CloudConfig cloudConfig;
    @Spy private DocumentConfig documentConfig = new DocumentConfig();
    @Mock private DocumentPathRepository documentPathRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private DocumentMapper documentMapper;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void uploadPublicDocument_ShouldSavePublicDocument_WhenFileIsValid() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                "content".getBytes()
        );
        UserAccount uploader = new UserAccount();

        when(userAccountRepository.findIdByUsername("hr.user")).thenReturn(Optional.of(7L));
        when(userAccountRepository.getReferenceById(7L)).thenReturn(uploader);
        when(cloudStorageService.store(file, "MaridOne", Directories.PUBLIC.getDisplayName()))
                .thenReturn("public-documents/MaridOne/policy.pdf");

        documentService.uploadPublicDocument(file, "hr.user");

        ArgumentCaptor<DocumentPath> captor = ArgumentCaptor.forClass(DocumentPath.class);
        verify(documentPathRepository).save(captor.capture());
        DocumentPath savedDocument = captor.getValue();

        Assertions.assertEquals("policy.pdf", savedDocument.getFileName());
        Assertions.assertEquals("application/pdf", savedDocument.getFileType());
        Assertions.assertEquals(file.getSize(), savedDocument.getFileSizeBytes());
        Assertions.assertEquals("public-documents/MaridOne/policy.pdf", savedDocument.getS3Key());
        Assertions.assertTrue(savedDocument.getIsPublic());
        Assertions.assertSame(uploader, savedDocument.getUserAccount());
    }

    @Test
    void uploadPublicDocument_ShouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        Assertions.assertThrows(
                InvalidActionException.class,
                () -> documentService.uploadPublicDocument(file, "hr.user")
        );

        verifyNoInteractions(cloudStorageService, documentPathRepository, userAccountRepository);
    }

    @Test
    void uploadPublicDocument_ShouldRejectOversizedFile() {
        byte[] content = new byte[(int) documentConfig.getMaxFileSize().toBytes() + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                content
        );

        Assertions.assertThrows(
                FileTooLargeException.class,
                () -> documentService.uploadPublicDocument(file, "hr.user")
        );

        verifyNoInteractions(cloudStorageService, documentPathRepository, userAccountRepository);
    }
}

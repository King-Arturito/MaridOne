package org.example.maridone.document.path;

import jakarta.persistence.*;
import org.example.maridone.core.user.UserAccount;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_path",
        indexes = {
                @Index(name = "idx_document_path_connected" ,  columnList = "connected_entity, connected_id", unique = false)
        })
public class DocumentPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "path_id", nullable = false)
    private Long pathId;

    //keep for now but most likely going to be replaced by s3_key;
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    //no direct connection
    @Column(name = "connected_entity", nullable = false)
    private String connectedEntity;

    //no direct connection
    //usage = connectedEntity -> connected Entity's primary key
    @Column(name = "connected_id", nullable = false)
    private Long connectedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount userAccount;

    public Long getPathId() {
        return pathId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getConnectedEntity() {
        return connectedEntity;
    }

    public void setConnectedEntity(String connectedEntity) {
        this.connectedEntity = connectedEntity;
    }

    public Long getConnectedId() {
        return connectedId;
    }

    public void setConnectedId(Long connectedId) {
        this.connectedId = connectedId;
    }
}

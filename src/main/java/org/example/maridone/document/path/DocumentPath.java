package org.example.maridone.document.path;

import jakarta.persistence.*;
import org.example.maridone.core.user.UserAccount;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

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

    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    //no direct connection
    @Column(name = "connected_entity", nullable = true)
    private String connectedEntity;

    //no direct connection
    //usage = connectedEntity -> Entity's primary key/s
    @Column(name = "connected_ids", nullable = true)
    private String connectedIds;

    @Column(name = "is_public")
    private Boolean isPublic = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount userAccount;

    public Long getPathId() {
        return pathId;
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

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
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

    public String getConnectedIds() {
        return connectedIds;
    }

    public void setConnectedIds(String connectedId) {
        this.connectedIds = connectedIds;
    }
}

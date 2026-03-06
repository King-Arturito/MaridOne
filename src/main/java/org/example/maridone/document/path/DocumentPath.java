package org.example.maridone.document.path;

import jakarta.persistence.*;
import org.example.maridone.core.user.UserAccount;

@Entity
@Table(name = "document_path")
public class DocumentPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "path_id", nullable = false)
    private Long pathId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @ManyToOne
    @JoinColumn(name = "username", nullable = false)
    private UserAccount username;

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

    public UserAccount getUsername() {
        return username;
    }

    public void setUsername(UserAccount username) {
        this.username = username;
    }
}

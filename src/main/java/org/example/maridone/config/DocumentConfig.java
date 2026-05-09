package org.example.maridone.config;

import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "document")
@Validated
public class DocumentConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentConfig.class);

    private static final long DEFAULT_MAX_FILE_UPLOAD = 5L;

    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    @Positive
    private long maxFileUpload = DEFAULT_MAX_FILE_UPLOAD;

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public long getMaxFileUpload() {
        return maxFileUpload;
    }

    public void setMaxFileUpload(long maxFileUpload) {
        this.maxFileUpload = maxFileUpload;
    }
}
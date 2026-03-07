package org.example.maridone.document.storage;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.CloudProperties;
import org.example.maridone.exception.CloudReadException;
import org.example.maridone.exception.CloudWriteException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Profile("prod")
public class CloudStorageService implements StorageService {

    private final S3Client s3Client;
    private final CloudProperties cloudProperties;
    public CloudStorageService(S3Client s3Client, CloudProperties cloudProperties) {
        this.s3Client = s3Client;
        this.cloudProperties = cloudProperties;
    }

    @Override
    @AuditLog
    @ExecutionTime
    public String store(MultipartFile file, String username) {
        try {
            String originalName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "unnamed";
            String cleanedName = StringUtils.cleanPath(originalName);
            String uniqueFileName = username + "/" + UUID.randomUUID() + "_" + cleanedName;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(cloudProperties.getBucketName())
                    .key(uniqueFileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return uniqueFileName;

        } catch (IOException e) {
            throw new CloudReadException("Failed to read file for upload to DigitalOcean Spaces", e);
        } catch (Exception e) {
            throw new CloudWriteException("Failed to upload file to DigitalOcean Spaces", e);
        }
    }

    @Override
    @ExecutionTime
    public Resource load(String filePath) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(cloudProperties.getBucketName())
                    .key(filePath)
                    .build();

            var s3ObjectStream = s3Client.getObject(getObjectRequest);
            return new InputStreamResource(s3ObjectStream);

        } catch (Exception e) {
            throw new CloudReadException("Could not read file from DigitalOcean Spaces at path: " + filePath, e);
        }
    }

    @Override
    @ExecutionTime
    public void delete(String filePath) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(cloudProperties.getBucketName())
                    .key(filePath)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

        } catch (Exception e) {
            throw new CloudReadException("Failed to delete file from DigitalOcean Spaces: " + filePath, e);
        }
    }
}
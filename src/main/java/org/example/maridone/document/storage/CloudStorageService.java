package org.example.maridone.document.storage;

import com.fasterxml.uuid.Generators;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.Sanitizer;
import org.example.maridone.config.CloudConfig;
import org.example.maridone.exception.servererror.CloudWriteException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;

@Service
public class CloudStorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final CloudConfig cloudConfig;

    public CloudStorageService(S3Client s3Client, S3Presigner s3Presigner, CloudConfig cloudConfig) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.cloudConfig = cloudConfig;
    }

    @Override
    @ExecutionTime
    @AuditLog
    public String store(MultipartFile file, String employeeId, String targetDir) {
        String sanitizedFilename = Sanitizer.sanitizeFilename(file.getOriginalFilename());
        if (!employeeId.matches("\\d+") && !employeeId.equals("MaridOne")) {
            throw new InvalidActionException("employeeId is not a valid value. employeeId = " + employeeId);
        }
         /*
            sample for public
                /cloudConfig.getCompanyDirectory()/MaridOne/UUID-filename
                /public-documents/MaridOne/UUID-filename

            sample for private(payslips):
                /payslip/employeeId/UUID-filename
         */
        String s3Key = String.format("%s/%s/%s-%s",
                targetDir,
                employeeId,
                Generators.timeBasedGenerator().generate().toString(),
                sanitizedFilename
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(cloudConfig.getPrivateBucketName())
                .key(s3Key)
                .contentType(file.getContentType())
                .build();

        try {
            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            return s3Key;
        } catch (IOException e) {
            throw new CloudWriteException("Failed to store file in S3", e);
        }
    }

    @Override
    @ExecutionTime
    public Resource load(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(cloudConfig.getPrivateBucketName())
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getObjectRequest);
        return new InputStreamResource(inputStream);
    }

    //for private buckets
    @Override
    @ExecutionTime
    public String generatePresignedUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(cloudConfig.getPrivateBucketName())
                .key(s3Key)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presignedRequest.url().toString();
    }

    @Override
    public void delete(String s3Key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(cloudConfig.getPrivateBucketName())
                .key(s3Key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }
}
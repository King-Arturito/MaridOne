package org.example.maridone.payroll.payslip;

import org.springframework.web.multipart.MultipartFile;

public record FileUploadBatchRequest(
        MultipartFile file,
        String username
) {}
package org.example.maridone.payroll.payslip;

import org.example.maridone.enums.Directories;
import org.springframework.web.multipart.MultipartFile;

public record FileUploadBatchRequest(
        MultipartFile file,
        String username,
        Directories subDir
) {}
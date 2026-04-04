package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.maridone.annotation.Upload;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.DocumentService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

@Component
@Aspect

public class UploadDocumentAspect {
    private final DocumentService documentService;
    private final UserAccountRepository userAccountRepository;

    public UploadDocumentAspect(DocumentService documentService,  UserAccountRepository userAccountRepository) {
        this.documentService = documentService;
        this.userAccountRepository = userAccountRepository;

    }

    @Pointcut("@annotation(org.example.maridone.annotation.Upload)")
    public void upload() {}

    @AfterReturning(pointcut = "upload()")
    public void uploadDocumentPath(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Upload upload = signature.getMethod().getAnnotation(Upload.class);
        String subDir = upload.subDir().getDisplayName();

        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Long empId = null;
        MultipartFile file = null;
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals("empId")) {
                empId = (Long) args[i];
                break;
            }
            if (paramNames[i].equals("file")) {
                file = (MultipartFile) args[i];
                break;
            }
        }
        if (empId == null) {
            throw new RuntimeException("@Upload must have an 'empId' parameter");
        }

        if (file != null)  {
            Long employeeId = empId;
            String username = userAccountRepository.findByEmployee_EmployeeId(employeeId)
                    .map(user -> user.getUsername())
                    .orElseThrow(() -> new RuntimeException("User account not found for employee id " + employeeId));
            documentService.upload(file, username, subDir);
        }
    }
}

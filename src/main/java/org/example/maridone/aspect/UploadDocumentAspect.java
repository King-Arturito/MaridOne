package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.maridone.annotation.Upload;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.employee.EmployeeService;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.NOCLOUD.LocalDocumentService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

@Component
@Aspect

public class UploadDocumentAspect {
    //todo: REPLACE FOR PROD
    private final LocalDocumentService localDocumentService;
    private final UserAccountRepository userAccountRepository;

    public UploadDocumentAspect(LocalDocumentService localDocumentService,  UserAccountRepository userAccountRepository) {
        this.localDocumentService = localDocumentService;
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
            String username = userAccountRepository.findByEmployeeId(empId).getUsername();
            localDocumentService.upload(file, username, subDir);
        }
    }
}

package org.example.maridone.exception.badrequest;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class MaxFileUploadExceededException extends BaseException {
    public MaxFileUploadExceededException(String message) {
        super(
                ErrorCode.MAX_FILE_UPLOAD_EXCEEDED,
                message);
    }
}

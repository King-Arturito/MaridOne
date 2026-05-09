package org.example.maridone.exception.toolarge;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class FileTooLargeException extends BaseException {
    public FileTooLargeException(String message) {
        super(
                ErrorCode.FILE_TOO_LARGE,
                message);
    }
}

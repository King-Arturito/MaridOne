package org.example.maridone.exception.unauthorized;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class UnauthorizedAccessException extends BaseException {
    public UnauthorizedAccessException(String message) {
        super(
                ErrorCode.UNAUTHORIZED,
                message);
    }
}

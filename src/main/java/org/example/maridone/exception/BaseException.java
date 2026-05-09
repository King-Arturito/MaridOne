package org.example.maridone.exception;

import org.example.maridone.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public abstract class BaseException extends RuntimeException {
    private final ErrorCode errorCode;
    protected BaseException(ErrorCode errorCode, String message)  {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

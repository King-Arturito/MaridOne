package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class RunNotFoundException extends BaseException {
    public RunNotFoundException(String message) {
        super(
                ErrorCode.RUN_NOT_FOUND,
                message);
    }
}

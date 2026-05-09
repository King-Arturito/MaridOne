package org.example.maridone.exception.badrequest;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class InvalidRangeException extends BaseException {
    public InvalidRangeException(String message) {
        super(
                ErrorCode.INVALID_RANGE,
                message
        );
    }
}

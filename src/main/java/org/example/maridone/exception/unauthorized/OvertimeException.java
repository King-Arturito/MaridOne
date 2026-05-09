package org.example.maridone.exception.unauthorized;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class OvertimeException extends BaseException {
    public OvertimeException(String message) {
        super(
                ErrorCode.OVERTIME_EXCEPTION,
                message);
    }
}

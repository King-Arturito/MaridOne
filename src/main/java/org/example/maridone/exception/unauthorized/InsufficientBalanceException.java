package org.example.maridone.exception.unauthorized;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class InsufficientBalanceException extends BaseException {
    public InsufficientBalanceException(String message) {
        super(
                ErrorCode.INSUFFICIENT_BALANCE,
                message);
    }
}

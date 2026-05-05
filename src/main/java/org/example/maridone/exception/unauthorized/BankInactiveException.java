package org.example.maridone.exception.unauthorized;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class BankInactiveException extends BaseException {
    public BankInactiveException(String message) {
        super(
                ErrorCode.BANK_INACTIVE,
                message
        );
    }
}

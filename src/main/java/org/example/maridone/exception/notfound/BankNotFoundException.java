package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class BankNotFoundException extends BaseException {
    public BankNotFoundException(String message) {
        super(
                ErrorCode.BANK_NOT_FOUND,
                message
        );
    }
}

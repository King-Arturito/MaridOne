package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class AccountNotFoundException extends BaseException {
    public AccountNotFoundException(String message) {
        super(
                ErrorCode.ACCOUNT_NOT_FOUND,
                message
        );
    }
}

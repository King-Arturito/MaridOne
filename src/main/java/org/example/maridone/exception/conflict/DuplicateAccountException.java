package org.example.maridone.exception.conflict;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class DuplicateAccountException extends BaseException {
    public DuplicateAccountException(String message) {
        super(
                ErrorCode.DUPLICATE_ACCOUNT,
                message);
    }
}

package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class BalanceNotFoundException extends BaseException {
    public BalanceNotFoundException(String message) {
        super(
                ErrorCode.BALANCE_NOT_FOUND,
                message
        );
    }
}

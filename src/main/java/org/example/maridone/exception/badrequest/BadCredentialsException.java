package org.example.maridone.exception.badrequest;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class BadCredentialsException extends BaseException {
    public BadCredentialsException() {
        super(
                ErrorCode.BAD_CREDENTIALS,
                "Invalid username or password"
        );
    }
}

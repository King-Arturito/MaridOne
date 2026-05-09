package org.example.maridone.exception.badrequest;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class InvalidRefreshTokenException extends BaseException {
    public InvalidRefreshTokenException() {
        super(
                ErrorCode.INVALID_REFRESH_TOKEN,
                "Invalid or expired refresh token.");
    }
}
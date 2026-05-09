package org.example.maridone.exception.preconditionrequired;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class SensitiveAccessRequiredException extends BaseException {
    public SensitiveAccessRequiredException() {
        super(
                ErrorCode.SENSITIVE_ACCESS_REQUIRED,
                "Sensitive access verification is required before opening this HR data.");
    }
}

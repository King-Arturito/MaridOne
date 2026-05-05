package org.example.maridone.exception.badrequest;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class InvalidActionException extends BaseException {
    public InvalidActionException(String message) {
        super(
                ErrorCode.INVALID_ACTION,
                message);
    }
}

package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class LeaveNotFoundException extends BaseException {
    public LeaveNotFoundException(String message) {
        super(
                ErrorCode.LEAVE_NOT_FOUND,
                message);
    }
}

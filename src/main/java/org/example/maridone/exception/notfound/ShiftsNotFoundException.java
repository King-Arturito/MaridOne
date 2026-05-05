package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class ShiftsNotFoundException extends BaseException {
    public ShiftsNotFoundException(String message) {
        super(
                ErrorCode.SHIFTS_NOT_FOUND,

                message);
    }
}

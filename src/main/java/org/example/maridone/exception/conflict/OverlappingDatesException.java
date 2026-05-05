package org.example.maridone.exception.conflict;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class OverlappingDatesException extends BaseException {
    public OverlappingDatesException(String message) {
        super(
                ErrorCode.OVERLAPPING_DATES,
                message);
    }
}

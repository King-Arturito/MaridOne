package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class CalendarEventNotFound extends BaseException {
    public CalendarEventNotFound(String message) {
        super(
                ErrorCode.CALENDAR_EVENT_NOT_FOUND,
                message
        );
    }
}

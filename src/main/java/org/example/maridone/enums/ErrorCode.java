package org.example.maridone.enums;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 404
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND),
    LEAVE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SHIFTS_NOT_FOUND(HttpStatus.NOT_FOUND),
    BANK_NOT_FOUND(HttpStatus.NOT_FOUND),
    CALENDAR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    BALANCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    PATH_NOT_FOUND(HttpStatus.NOT_FOUND),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND),

    // 401
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    NO_ROLE_ASSIGNED(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    // 400
    INVALID_ACTION(HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST),
    OVERTIME_EXCEPTION(HttpStatus.BAD_REQUEST),
    BANK_INACTIVE(HttpStatus.BAD_REQUEST),
    INVALID_RANGE(HttpStatus.BAD_REQUEST),
    MAX_FILE_UPLOAD_EXCEEDED(HttpStatus.BAD_REQUEST),
    MAX_UPLOAD_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST),

    // 409
    OVERLAPPING_DATES(HttpStatus.CONFLICT),
    DUPLICATE_LEAVE(HttpStatus.CONFLICT),
    DUPLICATE_DISPUTE(HttpStatus.CONFLICT),
    DUPLICATE_ACCOUNT(HttpStatus.CONFLICT),

    // 413
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    // 428
    SENSITIVE_ACCESS_REQUIRED(HttpStatus.PRECONDITION_REQUIRED),

    // 500
    CLOUD_READ_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    CLOUD_WRITE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),;

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
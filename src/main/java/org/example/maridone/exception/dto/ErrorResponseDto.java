package org.example.maridone.exception.dto;

import org.example.maridone.enums.ErrorCode;

import java.time.Instant;

public class ErrorResponseDto {

    private final String code;
    private final String message;
    private final int status;
    private final Instant timestamp;

    public ErrorResponseDto(ErrorCode code, String message) {
        this.code = code.name();
        this.message = message;
        this.status = code.getStatus().value();
        this.timestamp = Instant.now();
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public int getStatus() { return status; }
    public Instant getTimestamp() { return timestamp; }
}

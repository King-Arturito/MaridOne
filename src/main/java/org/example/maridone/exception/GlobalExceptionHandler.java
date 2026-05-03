package org.example.maridone.exception;

import org.example.maridone.auth.sensitive.SensitiveAccessRequiredException;
import org.example.maridone.exception.badrequest.BadCredentialsException;
import org.example.maridone.exception.badrequest.InvalidRefreshTokenException;
import org.example.maridone.exception.badrequest.NoRoleAssignedException;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.example.maridone.exception.conflict.OverlappingDatesException;
import org.example.maridone.exception.notfound.*;
import org.example.maridone.exception.toolarge.FileTooLargeException;
import org.example.maridone.exception.unauthorized.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //404 Not Found
    @ExceptionHandler({
            EmployeeNotFoundException.class,
            AccountNotFoundException.class,
            ItemNotFoundException.class,
            RequestNotFoundException.class,
            LeaveNotFoundException.class,
            ShiftsNotFoundException.class,
            BankNotFoundException.class,
            CalendarEventNotFound.class,
            BalanceNotFoundException.class,
            PathNotFoundException.class,
    })
    public ResponseEntity<Map<String, String>> handleNotFoundExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    //401 Unauthorized
    @ExceptionHandler({
            BadCredentialsException.class,
            NoRoleAssignedException.class,
            UnauthorizedAccessException.class,
            InvalidRefreshTokenException.class
    })
    public ResponseEntity<Map<String, String>> handleUnauthorizedExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    //400 Bad Request
    @ExceptionHandler({
            DuplicateLeaveException.class,
            DuplicateDisputeException.class,
            InvalidActionException.class,
            InsufficientBalanceException.class,
            OvertimeException.class,
            BankInactiveException.class,
            InvalidRangeException.class,
            DuplicateAccountException.class,
            CloudReadException.class,
            CloudWriteException.class,
            IllegalArgumentException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequestExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    //409 Conflict
    @ExceptionHandler({
            OverlappingDatesException.class
    })
    public ResponseEntity<Map<String, String>> handleConflictExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    //413 Payload too large
    @ExceptionHandler({
            FileTooLargeException.class,
            MaxUploadSizeExceededException.class,
            MultipartException.class
    })
    public ResponseEntity<Map<String, String>> handleLargePayloadExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SensitiveAccessRequiredException.class)
    public ResponseEntity<Map<String, String>> handleSensitiveAccessRequired(SensitiveAccessRequiredException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedExceptions(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Unexpected server error."));
    }
}

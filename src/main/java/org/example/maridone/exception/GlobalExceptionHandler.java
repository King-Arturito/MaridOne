package org.example.maridone.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
            RuntimeException.class
    })
    public ResponseEntity<Map<String, String>> handleNotFoundExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    //401 Unauthorized
    @ExceptionHandler({
            BadCredentialsException.class,
            NoRoleAssignedException.class,
            UnauthorizedAccessException.class
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
    })
    public ResponseEntity<Map<String, String>> handleBadRequestExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}

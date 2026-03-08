package org.example.maridone.exception;

public class ShiftsNotFoundException extends RuntimeException {
    public ShiftsNotFoundException(String message) {
        super(message);
    }
}

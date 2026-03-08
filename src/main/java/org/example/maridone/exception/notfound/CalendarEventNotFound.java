package org.example.maridone.exception;

public class CalendarEventNotFound extends RuntimeException {
    public CalendarEventNotFound(String message) {
        super(message);
    }
}

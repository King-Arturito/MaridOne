package org.example.maridone.exception;

public class CloudWriteException extends RuntimeException {
    public CloudWriteException(String message, Exception e) {
        super(message, e);
    }
}

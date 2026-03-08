package org.example.maridone.exception.unauthorized;

public class CloudReadException extends RuntimeException {
    public CloudReadException(String message, Exception e) {
        super(message, e);
    }
}

package org.example.maridone.exception.servererror;

public class CloudReadException extends RuntimeException {
    public CloudReadException(String message, Exception e) {
        super(message, e);
    }
}

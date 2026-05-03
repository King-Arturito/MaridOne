package org.example.maridone.exception.badrequest;

public class MaxFileUploadExceededException extends RuntimeException {
    public MaxFileUploadExceededException(String message) {
        super(message);
    }
}

package org.example.maridone.exception.notfound;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(String requestType, Long id) {
        super(requestType + " ID: "+ id + " not found.");
    }
}

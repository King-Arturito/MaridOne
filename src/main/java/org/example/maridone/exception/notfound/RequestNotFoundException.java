package org.example.maridone.exception.notfound;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(Long id) {
        super("Dispute Request of id: " + id + " not found.");
    }
}

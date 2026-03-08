package org.example.maridone.exception.unauthorized;

public class BankInactiveException extends RuntimeException {
    public BankInactiveException(String message) {
        super(message);
    }
}

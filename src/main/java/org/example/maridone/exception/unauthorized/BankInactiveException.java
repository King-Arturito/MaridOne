package org.example.maridone.exception;

public class BankInactiveException extends RuntimeException {
    public BankInactiveException(String message) {
        super(message);
    }
}

package org.example.maridone.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String username) {
        super("Account with username: " + username +  "  not found");
    }
}

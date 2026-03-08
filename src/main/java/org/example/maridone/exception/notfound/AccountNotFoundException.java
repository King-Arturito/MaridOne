package org.example.maridone.exception.notfound;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String username) {
        super("Account with username: " + username +  "  not found");
    }
}

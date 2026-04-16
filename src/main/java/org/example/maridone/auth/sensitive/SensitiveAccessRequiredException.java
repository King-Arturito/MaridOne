package org.example.maridone.auth.sensitive;

public class SensitiveAccessRequiredException extends RuntimeException {
    public SensitiveAccessRequiredException() {
        super("Sensitive access verification is required before opening this HR data.");
    }
}

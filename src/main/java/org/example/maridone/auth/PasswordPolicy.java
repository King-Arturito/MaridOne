package org.example.maridone.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class PasswordPolicy {
    public static final int USER_SELECTED_PASSWORD_MIN_LENGTH = 8;
    public static final long SELF_SERVICE_PASSWORD_CHANGE_COOLDOWN_DAYS = 30;

    private PasswordPolicy() {
    }

    public static String requireUserSelectedPassword(String password, String requiredMessage) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException(requiredMessage);
        }

        String normalized = password.trim();
        if (normalized.length() < USER_SELECTED_PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "New password must be at least " + USER_SELECTED_PASSWORD_MIN_LENGTH + " characters long."
            );
        }

        return normalized;
    }

    public static Instant getSelfServicePasswordChangeAvailableAt(Instant lastPasswordChangedAt) {
        if (lastPasswordChangedAt == null) {
            return null;
        }
        return lastPasswordChangedAt.plus(SELF_SERVICE_PASSWORD_CHANGE_COOLDOWN_DAYS, ChronoUnit.DAYS);
    }

    public static boolean canSelfServicePasswordBeChanged(boolean passwordChangeRequired,
                                                          Instant lastPasswordChangedAt,
                                                          Instant now) {
        if (passwordChangeRequired) {
            return true;
        }

        Instant availableAt = getSelfServicePasswordChangeAvailableAt(lastPasswordChangedAt);
        return availableAt == null || !availableAt.isAfter(now);
    }
}

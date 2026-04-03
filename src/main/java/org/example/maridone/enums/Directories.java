package org.example.maridone.enums;

public enum Directories {
    PAYSLIP("payslip"),
    LEAVE_REQUEST("leave-request"),
    MATERIALS("training-materials"),
    DISPUTE("dispute-request");

    private String displayName;

    Directories(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package org.example.maridone.enums;

public enum ManagerTitle {
    HEAD_OF_PEOPLE_OPERATIONS("People Operations Department"),
    HEAD_OF_PROCUREMENT("Procurements Department"),
    PRODUCTION_SUPERVISOR("Productions Department"),
    PRODUCT_DEVELOPMENT_ENGINEER("Product Development Department");

    private final String displayName;

    ManagerTitle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

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

    public static ManagerTitle fromDepartmentLabel(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Department is required when position is MANAGER.");
        }

        String normalized = normalize(value);
        for (ManagerTitle title : values()) {
            if (normalize(title.displayName).equals(normalized) || title.name().equals(normalized)) {
                return title;
            }
        }

        throw new IllegalArgumentException("Department must match an available manager department.");
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }
}

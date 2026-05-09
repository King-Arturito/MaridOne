package org.example.maridone.common;

public class Sanitizer {
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "default_file";
        }

        //decompose accented characters (ñ → n)
        filename = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFD);
        filename = filename.replaceAll("\\p{M}", "");

        String sanitized = filename
                .replaceAll("[\\s]+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "")
                .replaceAll("_{2,}", "_")
                .toLowerCase();

        if (sanitized.isEmpty()) {
            return "default_file";
        }

        //prevents hidden files on linux
        if (sanitized.startsWith(".")) {
            sanitized = "file" + sanitized;
        }

        //strip trailing dots, hyphens, underscores
        sanitized = sanitized.replaceAll("[._-]+$", "");

        //recheck emptiness after removing chars
        if (sanitized.isEmpty()) {
            return "default_file";
        }

        //truncate before reserved-name check so safe_ prefix is never at risk of being cut
        if (sanitized.length() > 200) {
            int extIndex = sanitized.lastIndexOf(".");
            if (extIndex != -1) {
                String ext = sanitized.substring(extIndex);
                sanitized = sanitized.substring(0, 200 - ext.length()) + ext;
            } else {
                sanitized = sanitized.substring(0, 200);
            }
        }

        // (?i) makes the regex self-contained — no silent dependency on prior toLowerCase()
        if (isReserved(sanitized)) {
            sanitized = "safe_" + sanitized;
        }

        return sanitized;
    }

    private static boolean isReserved(String name) {
        return name.matches("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$");
    }
}
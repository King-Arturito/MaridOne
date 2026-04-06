package org.example.maridone.notification;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.payroll.run.PayrollRun;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class NotificationMessageFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, uuuu");

    private NotificationMessageFormatter() {}

    public static String formatEmployeeName(Employee employee) {
        if (employee == null) {
            return "An employee";
        }

        String fullName = Stream.of(employee.getFirstName(), employee.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));

        return fullName.isBlank() ? "An employee" : fullName;
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "an unspecified date" : date.format(DATE_FORMATTER);
    }

    public static String formatDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return "an unspecified date";
        }
        if (startDate == null) {
            return formatDate(endDate);
        }
        if (endDate == null || startDate.equals(endDate)) {
            return formatDate(startDate);
        }
        return formatDate(startDate) + " to " + formatDate(endDate);
    }

    public static String formatPayrollPeriod(PayrollRun payrollRun) {
        if (payrollRun == null) {
            return "an unspecified payroll period";
        }
        return formatDateRange(payrollRun.getPeriodStart(), payrollRun.getPeriodEnd());
    }

    public static String formatAnnouncementMessage(
            Employee employee,
            Instant startDate,
            Instant endDate,
            String title,
            ZoneId zoneId
    ) {
        String announcementTitle = title == null || title.isBlank() ? "Untitled announcement" : title.trim();
        String message = formatEmployeeName(employee)
                + " made an announcement for "
                + formatAnnouncementDateRange(startDate, endDate, zoneId)
                + ": "
                + announcementTitle;
        return endsWithTerminalPunctuation(announcementTitle) ? message : message + ".";
    }

    public static String formatAnnouncementDateRange(Instant startDate, Instant endDate, ZoneId zoneId) {
        ZoneId resolvedZoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        LocalDate normalizedStart = startDate == null ? null : startDate.atZone(resolvedZoneId).toLocalDate();
        LocalDate normalizedEnd = endDate == null ? null : endDate.atZone(resolvedZoneId).toLocalDate();

        if (normalizedStart == null && normalizedEnd == null) {
            return "an unspecified date - an unspecified date";
        }

        LocalDate safeStart = normalizedStart == null ? normalizedEnd : normalizedStart;
        LocalDate safeEnd = normalizedEnd == null ? normalizedStart : normalizedEnd;

        return formatDate(safeStart) + " - " + formatDate(safeEnd);
    }

    private static boolean endsWithTerminalPunctuation(String value) {
        return value.endsWith(".") || value.endsWith("!") || value.endsWith("?");
    }
}

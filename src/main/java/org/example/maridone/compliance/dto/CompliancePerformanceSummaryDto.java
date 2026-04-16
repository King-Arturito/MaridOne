package org.example.maridone.compliance.dto;

public class CompliancePerformanceSummaryDto {
    private int windowDays;
    private int attendanceDays;
    private int completeLogPairs;
    private int incompleteLogDays;
    private double totalHours;
    private double averageHoursPerAttendanceDay;
    private double averageHoursPerWindowDay;

    public int getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }

    public int getAttendanceDays() {
        return attendanceDays;
    }

    public void setAttendanceDays(int attendanceDays) {
        this.attendanceDays = attendanceDays;
    }

    public int getCompleteLogPairs() {
        return completeLogPairs;
    }

    public void setCompleteLogPairs(int completeLogPairs) {
        this.completeLogPairs = completeLogPairs;
    }

    public int getIncompleteLogDays() {
        return incompleteLogDays;
    }

    public void setIncompleteLogDays(int incompleteLogDays) {
        this.incompleteLogDays = incompleteLogDays;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    public double getAverageHoursPerAttendanceDay() {
        return averageHoursPerAttendanceDay;
    }

    public void setAverageHoursPerAttendanceDay(double averageHoursPerAttendanceDay) {
        this.averageHoursPerAttendanceDay = averageHoursPerAttendanceDay;
    }

    public double getAverageHoursPerWindowDay() {
        return averageHoursPerWindowDay;
    }

    public void setAverageHoursPerWindowDay(double averageHoursPerWindowDay) {
        this.averageHoursPerWindowDay = averageHoursPerWindowDay;
    }
}

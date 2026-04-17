package org.example.maridone.compliance.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ComplianceRecordDto {
    private Long employeeId;
    private String employeeName;
    private String username;
    private String employmentStatus;
    private String position;
    private String department;
    private LocalDate employmentDateStart;
    private LocalDate employmentDateEnd;
    private List<ComplianceDocumentDto> documents = new ArrayList<>();
    private List<ComplianceLeaveRecordDto> leaveRequests = new ArrayList<>();
    private List<ComplianceDisputeRecordDto> disputes = new ArrayList<>();
    private List<ComplianceDisciplinaryActionDto> disciplinaryActions = new ArrayList<>();
    private CompliancePerformanceSummaryDto performanceSummary;
    private List<ComplianceTimelineEntryDto> timeline = new ArrayList<>();

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public LocalDate getEmploymentDateStart() {
        return employmentDateStart;
    }

    public void setEmploymentDateStart(LocalDate employmentDateStart) {
        this.employmentDateStart = employmentDateStart;
    }

    public LocalDate getEmploymentDateEnd() {
        return employmentDateEnd;
    }

    public void setEmploymentDateEnd(LocalDate employmentDateEnd) {
        this.employmentDateEnd = employmentDateEnd;
    }

    public List<ComplianceDocumentDto> getDocuments() {
        return documents;
    }

    public void setDocuments(List<ComplianceDocumentDto> documents) {
        this.documents = documents;
    }

    public List<ComplianceLeaveRecordDto> getLeaveRequests() {
        return leaveRequests;
    }

    public void setLeaveRequests(List<ComplianceLeaveRecordDto> leaveRequests) {
        this.leaveRequests = leaveRequests;
    }

    public List<ComplianceDisputeRecordDto> getDisputes() {
        return disputes;
    }

    public void setDisputes(List<ComplianceDisputeRecordDto> disputes) {
        this.disputes = disputes;
    }

    public List<ComplianceDisciplinaryActionDto> getDisciplinaryActions() {
        return disciplinaryActions;
    }

    public void setDisciplinaryActions(List<ComplianceDisciplinaryActionDto> disciplinaryActions) {
        this.disciplinaryActions = disciplinaryActions;
    }

    public CompliancePerformanceSummaryDto getPerformanceSummary() {
        return performanceSummary;
    }

    public void setPerformanceSummary(CompliancePerformanceSummaryDto performanceSummary) {
        this.performanceSummary = performanceSummary;
    }

    public List<ComplianceTimelineEntryDto> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<ComplianceTimelineEntryDto> timeline) {
        this.timeline = timeline;
    }
}

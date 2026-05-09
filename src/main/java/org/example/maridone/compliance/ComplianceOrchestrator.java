package org.example.maridone.compliance;

import org.example.maridone.compliance.dto.ComplianceDisciplinaryActionDto;
import org.example.maridone.compliance.dto.ComplianceDisputeRecordDto;
import org.example.maridone.compliance.dto.ComplianceDocumentDto;
import org.example.maridone.compliance.dto.ComplianceLeaveRecordDto;
import org.example.maridone.compliance.dto.CompliancePerformanceSummaryDto;
import org.example.maridone.compliance.dto.ComplianceRecordDto;
import org.example.maridone.compliance.dto.ComplianceTimelineEntryDto;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.employee.EmployeeSeparation;
import org.example.maridone.core.employee.EmployeeSeparationRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.dto.DocumentLinkDto;
import org.example.maridone.document.path.DocumentPathLink;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.spec.DocumentSpecs;
import org.example.maridone.enums.Activity;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.ActivityLogRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.dispute.DisputeRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ComplianceOrchestrator {
    private static final int MAX_HISTORY_ROWS = 200;
    private static final int PERFORMANCE_WINDOW_DAYS = 30;
    private static final DateTimeFormatter TIMELINE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH);

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DisputeRequestRepository disputeRequestRepository;
    private final DocumentPathRepository documentPathRepository;
    private final EmployeeSeparationRepository employeeSeparationRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final DefaultConfig defaultConfig;

    public ComplianceOrchestrator(EmployeeRepository employeeRepository,
                                  UserAccountRepository userAccountRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  DisputeRequestRepository disputeRequestRepository,
                                  DocumentPathRepository documentPathRepository,
                                  EmployeeSeparationRepository employeeSeparationRepository,
                                  ActivityLogRepository activityLogRepository,
                                  AttendanceLogRepository attendanceLogRepository,
                                  DefaultConfig defaultConfig){
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.disputeRequestRepository = disputeRequestRepository;
        this.documentPathRepository = documentPathRepository;
        this.employeeSeparationRepository = employeeSeparationRepository;
        this.activityLogRepository = activityLogRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.defaultConfig = defaultConfig;
    }

    public ComplianceRecordDto getEmployeeComplianceRecord(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        ComplianceRecordDto dto = new ComplianceRecordDto();
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setEmployeeName(formatEmployeeName(employee));
        dto.setUsername(userAccountRepository.findUsernameByEmployeeEmployeeId(employeeId).orElse(null));
        dto.setEmploymentStatus(humanizeToken(employee.getEmploymentStatus() != null ? employee.getEmploymentStatus().name() : null));
        dto.setPosition(humanizeToken(employee.getPosition() != null ? employee.getPosition().name() : null));
        dto.setDepartment(resolveDepartment(employee));
        dto.setEmploymentDateStart(employee.getEmploymentDateStart());
        dto.setEmploymentDateEnd(employee.getEmploymentDateEnd());

        List<EmployeeSeparation> separations =
                employeeSeparationRepository.findAllByEmployee_EmployeeIdOrderByProcessedAtDescSeparationIdDesc(employeeId);
        List<LeaveRequest> leaveRequests = leaveRequestRepository.findByEmployee_EmployeeId(
                employeeId,
                PageRequest.of(0, MAX_HISTORY_ROWS, Sort.by(Sort.Direction.DESC, "requestId"))
        ).getContent();
        List<DisputeRequest> disputes = disputeRequestRepository.findAllByPayrollItem_Employee_EmployeeId(
                employeeId,
                PageRequest.of(0, MAX_HISTORY_ROWS, Sort.by(Sort.Direction.DESC, "disputeId"))
        ).getContent();
        List<DocumentPath> documents = loadEmployeeDocuments(dto.getUsername());
        List<ComplianceDisciplinaryActionDto> disciplinaryActions = loadDisciplinaryActions(employeeId);
        CompliancePerformanceSummaryDto performanceSummary = buildPerformanceSummary(employeeId);

        dto.setDocuments(mapDocuments(documents));
        dto.setLeaveRequests(mapLeaveRequests(leaveRequests));
        dto.setDisputes(mapDisputes(disputes));
        dto.setDisciplinaryActions(disciplinaryActions);
        dto.setPerformanceSummary(performanceSummary);
        dto.setTimeline(buildTimeline(employee, separations, leaveRequests, disputes, disciplinaryActions));
        return dto;
    }

    private List<DocumentPath> loadEmployeeDocuments(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        return documentPathRepository.findAll(
                DocumentSpecs.hasUsername(username),
                Sort.by(Sort.Direction.DESC, "pathId")
        );
    }

    private List<ComplianceDocumentDto> mapDocuments(List<DocumentPath> documents) {
        return documents.stream()
                .map(document -> new ComplianceDocumentDto(
                        document.getPathId(),
                        document.getFileName(),
                        document.getFileType(),
                        document.getFileSizeBytes(),
                        document.getLinks().stream()
                                .map(link -> new DocumentLinkDto(
                                        link.getConnectedEntity(),
                                        link.getConnectedId()
                                ))
                                .toList()
                ))
                .toList();
    }

    private List<ComplianceLeaveRecordDto> mapLeaveRequests(List<LeaveRequest> requests) {
        return requests.stream()
                .map(request -> {
                    ComplianceLeaveRecordDto dto = new ComplianceLeaveRecordDto();
                    dto.setRequestId(request.getRequestId());
                    dto.setLeaveType(humanizeToken(request.getLeaveType() != null ? request.getLeaveType().name() : null));
                    dto.setStatus(humanizeToken(request.getRequestStatus() != null ? request.getRequestStatus().name() : null));
                    dto.setPaid(request.getPaid());
                    dto.setStartDateTime(request.getStartDateTime());
                    dto.setEndDateTime(request.getEndDateTime());
                    dto.setReason(request.getReason());
                    dto.setApprover(request.getApprover());
                    dto.setApproverReason(request.getApproverReason());
                    dto.setApprovedAt(request.getApprovedAt());
                    return dto;
                })
                .toList();
    }

    private List<ComplianceDisputeRecordDto> mapDisputes(List<DisputeRequest> disputes) {
        return disputes.stream()
                .map(dispute -> {
                    ComplianceDisputeRecordDto dto = new ComplianceDisputeRecordDto();
                    dto.setDisputeId(dispute.getDisputeId());
                    dto.setPayrollItemId(dispute.getPayrollItem() != null ? dispute.getPayrollItem().getItemId() : null);
                    dto.setSubject(dispute.getSubject());
                    dto.setReason(dispute.getReason());
                    dto.setStatus(humanizeToken(dispute.getStatus() != null ? dispute.getStatus().name() : null));
                    dto.setStatusReason(dispute.getStatusReason());
                    dto.setApprover(dispute.getApprover());
                    dto.setCreatedAt(dispute.getCreatedAt());
                    dto.setUpdatedAt(dispute.getUpdatedAt());
                    return dto;
                })
                .toList();
    }

    private List<ComplianceDisciplinaryActionDto> loadDisciplinaryActions(Long employeeId) {
        return activityLogRepository.findByActivityTypeAndEmployeeReference(Activity.MODIFY, employeeId).stream()
                .map(this::toDisciplinaryAction)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<ComplianceDisciplinaryActionDto> toDisciplinaryAction(ActivityLog activityLog) {
        Map<String, String> payload = parseDetailPayload(activityLog.getDetailPayload());
        String subject = payload.get("Subject");
        String currentStatus = payload.get("Current Status");
        String summary = payload.getOrDefault("Summary", activityLog.getMessage());

        if (!"Employee Status Change".equalsIgnoreCase(subject != null ? subject.trim() : "")
                || !"Suspended".equalsIgnoreCase(currentStatus != null ? currentStatus.trim() : "")) {
            return Optional.empty();
        }

        ComplianceDisciplinaryActionDto dto = new ComplianceDisciplinaryActionDto();
        dto.setAction("Suspension");
        dto.setStatus(currentStatus);
        dto.setSummary(summary);
        dto.setNotes(payload.getOrDefault("Notes", payload.get("Change")));
        dto.setRecordedBy(payload.getOrDefault("Username", activityLog.getUsername()));
        dto.setRecordedAt(activityLog.getTimestamp());
        return Optional.of(dto);
    }

    private CompliancePerformanceSummaryDto buildPerformanceSummary(Long employeeId) {
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofDays(PERFORMANCE_WINDOW_DAYS));
        List<AttendanceLog> logs = attendanceLogRepository.findByEmployeeIdAndTimestampBetween(
                employeeId,
                start,
                end,
                Sort.by(Sort.Direction.ASC, "timestamp", "attendanceId")
        );

        Map<LocalDate, List<AttendanceLog>> logsByDate = logs.stream()
                .collect(Collectors.groupingBy(log -> LocalDateTime.ofInstant(log.getTimestamp(), defaultConfig.getTimeZone()).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Duration totalDuration = Duration.ZERO;
        int completeLogPairs = 0;
        int incompleteLogDays = 0;

        for (List<AttendanceLog> dayLogs : logsByDate.values()) {
            Deque<AttendanceLog> queue = new ArrayDeque<>(dayLogs);
            boolean hasIncomplete = false;
            while (!queue.isEmpty()) {
                AttendanceLog current = queue.pollFirst();
                if (current == null) {
                    continue;
                }
                if (!"IN".equalsIgnoreCase(current.getDirection())) {
                    hasIncomplete = true;
                    continue;
                }

                AttendanceLog next = queue.peekFirst();
                if (next == null || !"OUT".equalsIgnoreCase(next.getDirection())) {
                    hasIncomplete = true;
                    continue;
                }

                queue.pollFirst();
                if (next.getTimestamp().isAfter(current.getTimestamp())) {
                    totalDuration = totalDuration.plus(Duration.between(current.getTimestamp(), next.getTimestamp()));
                    completeLogPairs++;
                }
            }

            if (hasIncomplete) {
                incompleteLogDays++;
            }
        }

        CompliancePerformanceSummaryDto dto = new CompliancePerformanceSummaryDto();
        dto.setWindowDays(PERFORMANCE_WINDOW_DAYS);
        dto.setAttendanceDays(logsByDate.size());
        dto.setCompleteLogPairs(completeLogPairs);
        dto.setIncompleteLogDays(incompleteLogDays);
        dto.setTotalHours(roundHours(totalDuration));
        dto.setAverageHoursPerAttendanceDay(logsByDate.isEmpty() ? 0.0 : round(totalDuration.toMinutes() / 60.0 / logsByDate.size()));
        dto.setAverageHoursPerWindowDay(round(totalDuration.toMinutes() / 60.0 / PERFORMANCE_WINDOW_DAYS));
        return dto;
    }

    private List<ComplianceTimelineEntryDto> buildTimeline(Employee employee,
                                                          List<EmployeeSeparation> separations,
                                                          List<LeaveRequest> leaveRequests,
                                                          List<DisputeRequest> disputes,
                                                          List<ComplianceDisciplinaryActionDto> disciplinaryActions) {
        List<ComplianceTimelineEntryDto> entries = new ArrayList<>();

        if (employee.getEmploymentDateStart() != null) {
            entries.add(buildTimelineEntry(
                    "Employment",
                    "Employment Started",
                    "Employee joined on " + TIMELINE_DATE_FORMAT.format(employee.getEmploymentDateStart()) + ".",
                    employee.getEmploymentDateStart().atStartOfDay(defaultConfig.getTimeZone()).toInstant()
            ));
        }

        for (EmployeeSeparation separation : separations) {
            Instant timestamp = separation.getProcessedAt() != null
                    ? separation.getProcessedAt().atZone(defaultConfig.getTimeZone()).toInstant()
                    : separation.getEffectiveDate() != null
                        ? separation.getEffectiveDate().atStartOfDay(defaultConfig.getTimeZone()).toInstant()
                        : Instant.EPOCH;
            entries.add(buildTimelineEntry(
                    "Separation",
                    humanizeToken(separation.getReason() != null ? separation.getReason().name() : "Separation"),
                    buildSeparationDetail(separation),
                    timestamp
            ));
        }

        for (LeaveRequest leaveRequest : leaveRequests) {
            Instant timestamp = leaveRequest.getStartDateTime() != null
                    ? leaveRequest.getStartDateTime().atZone(defaultConfig.getTimeZone()).toInstant()
                    : leaveRequest.getApprovedAt();
            entries.add(buildTimelineEntry(
                    "Leave",
                    humanizeToken(leaveRequest.getLeaveType() != null ? leaveRequest.getLeaveType().name() : "Leave"),
                    buildLeaveTimelineDetail(leaveRequest),
                    timestamp
            ));
        }

        for (DisputeRequest dispute : disputes) {
            entries.add(buildTimelineEntry(
                    "Dispute",
                    dispute.getSubject() == null || dispute.getSubject().isBlank() ? "Payroll Dispute" : dispute.getSubject(),
                    buildDisputeTimelineDetail(dispute),
                    Optional.ofNullable(dispute.getUpdatedAt()).orElse(dispute.getCreatedAt())
            ));
        }

        for (ComplianceDisciplinaryActionDto action : disciplinaryActions) {
            entries.add(buildTimelineEntry(
                    "Disciplinary",
                    action.getAction(),
                    action.getSummary(),
                    action.getRecordedAt()
            ));
        }

        return entries.stream()
                .filter(entry -> entry.getTimestamp() != null)
                .sorted(Comparator.comparing(ComplianceTimelineEntryDto::getTimestamp).reversed())
                .limit(MAX_HISTORY_ROWS)
                .toList();
    }

    private ComplianceTimelineEntryDto buildTimelineEntry(String type, String title, String detail, Instant timestamp) {
        ComplianceTimelineEntryDto dto = new ComplianceTimelineEntryDto();
        dto.setType(type);
        dto.setTitle(title);
        dto.setDetail(detail);
        dto.setTimestamp(timestamp);
        return dto;
    }

    private String buildSeparationDetail(EmployeeSeparation separation) {
        List<String> parts = new ArrayList<>();
        if (separation.getEffectiveDate() != null) {
            parts.add("Effective " + TIMELINE_DATE_FORMAT.format(separation.getEffectiveDate()));
        }
        if (separation.getEligibleForRehire() != null) {
            parts.add("Rehire: " + (Boolean.TRUE.equals(separation.getEligibleForRehire()) ? "Yes" : "No"));
        }
        if (separation.getProcessedBy() != null && !separation.getProcessedBy().isBlank()) {
            parts.add("Recorded by " + separation.getProcessedBy().trim());
        }
        if (separation.getNotes() != null && !separation.getNotes().isBlank()) {
            parts.add(separation.getNotes().trim());
        }
        return String.join(". ", parts);
    }

    private String buildLeaveTimelineDetail(LeaveRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getStartDateTime() != null && request.getEndDateTime() != null) {
            LocalDate start = request.getStartDateTime().toLocalDate();
            LocalDate end = request.getEndDateTime().toLocalDate();
            parts.add(start.equals(end)
                    ? TIMELINE_DATE_FORMAT.format(start)
                    : TIMELINE_DATE_FORMAT.format(start) + " to " + TIMELINE_DATE_FORMAT.format(end));
        }
        if (request.getRequestStatus() != null) {
            parts.add("Status: " + humanizeToken(request.getRequestStatus().name()));
        }
        if (request.getPaid() != null) {
            parts.add(Boolean.TRUE.equals(request.getPaid()) ? "Paid" : "Unpaid");
        }
        return String.join(". ", parts);
    }

    private String buildDisputeTimelineDetail(DisputeRequest dispute) {
        List<String> parts = new ArrayList<>();
        if (dispute.getStatus() != null) {
            parts.add("Status: " + humanizeToken(dispute.getStatus().name()));
        }
        if (dispute.getStatusReason() != null && !dispute.getStatusReason().isBlank()) {
            parts.add(dispute.getStatusReason().trim());
        }
        return String.join(". ", parts);
    }

    private Map<String, String> parseDetailPayload(String detailPayload) {
        if (detailPayload == null || detailPayload.isBlank()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : detailPayload.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (!key.isBlank() && !value.isBlank()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private String classifyDocumentCategory(String filePath) {
        String normalized = filePath == null ? "" : filePath.replace('\\', '/').toLowerCase(Locale.ENGLISH);
        if (normalized.contains("/leave-request/")) {
            return "Leave Request Attachment";
        }
        if (normalized.contains("/dispute-request/")) {
            return "Dispute Attachment";
        }
        if (normalized.contains("/payslip/")) {
            return "Payslip File";
        }
        return "Employee File";
    }

    private String resolveDepartment(Employee employee) {
        Employee current = employee;
        while (current != null) {
            if (current.getEmpTitle() != null) {
                return current.getEmpTitle().getDisplayName();
            }
            current = current.getManager();
        }
        return null;
    }

    private String formatEmployeeName(Employee employee) {
        return Stream.of(employee.getFirstName(), employee.getMiddleName(), employee.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String humanizeToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Stream.of(value.trim().split("_+"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ENGLISH) + part.substring(1).toLowerCase(Locale.ENGLISH))
                .collect(Collectors.joining(" "));
    }

    private double roundHours(Duration duration) {
        return round(duration.toMinutes() / 60.0);
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}

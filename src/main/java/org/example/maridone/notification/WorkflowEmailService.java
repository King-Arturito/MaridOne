package org.example.maridone.notification;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.auth.email.EmailService;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static java.util.concurrent.CompletableFuture.runAsync;

@Service
public class WorkflowEmailService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEmailService.class);

    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final DefaultConfig defaultConfig;

    public WorkflowEmailService(
            EmailService emailService,
            EmployeeRepository employeeRepository,
            DefaultConfig defaultConfig
    ) {
        this.emailService = emailService;
        this.employeeRepository = employeeRepository;
        this.defaultConfig = defaultConfig;
    }

    public void sendLeaveSubmittedToManager(Employee employee, LeaveRequestDto payload) {
        if (employee == null || payload == null) {
            return;
        }

        Employee manager = resolveManagerRecipient(employee);
        if (manager == null) {
            return;
        }

        String employeeName = NotificationMessageFormatter.formatEmployeeName(employee);
        String leaveType = payload.getLeaveType() == null ? "Leave" : formatLabel(payload.getLeaveType().name());
        String leaveDates = NotificationMessageFormatter.formatDateRange(
                safeDate(payload.getStartDateTime()),
                safeDate(payload.getEndDateTime())
        );
        String subject = "New Leave Request Awaiting Manager Review";
        String body = employeeName + " submitted a " + leaveType + " request for " + leaveDates + "."
                + "\nReason: " + safeText(payload.getReason(), "No reason provided.")
                + portalFooter("Please log in to MaridOne to review this leave request.");

        sendAfterCommit(resolveEmployeeEmails(manager), subject, body);
    }

    public void sendLeaveSubmittedToManagement(Employee employee, LeaveRequestDto payload) {
        if (employee == null || payload == null) {
            return;
        }

        String employeeName = NotificationMessageFormatter.formatEmployeeName(employee);
        String leaveType = payload.getLeaveType() == null ? "Leave" : formatLabel(payload.getLeaveType().name());
        String leaveDates = NotificationMessageFormatter.formatDateRange(
                safeDate(payload.getStartDateTime()),
                safeDate(payload.getEndDateTime())
        );
        String subject = "New Leave Request Awaiting Management Review";
        String body = employeeName + " submitted a " + leaveType + " request for " + leaveDates + "."
                + "\nReason: " + safeText(payload.getReason(), "No reason provided.")
                + portalFooter("Please log in to MaridOne to review this leave request.");

        sendAfterCommit(resolveManagementEmails(), subject, body);
    }

    public void sendLeaveForwardedToHr(LeaveRequest request) {
        if (request == null) {
            return;
        }

        String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
        String leaveDates = NotificationMessageFormatter.formatDateRange(
                safeDate(request.getStartDateTime()),
                safeDate(request.getEndDateTime())
        );
        String firstLevelApproverTitle = firstLevelApproverTitle(request.getEmployee());
        String subject = "Leave Request Ready for HR Review";
        String body = employeeName + "'s leave request for " + leaveDates + " is ready for HR review."
                + "\nLeave type: " + safeText(request.getLeaveType() == null ? null : formatLabel(request.getLeaveType().name()), "Unspecified")
                + "\n" + firstLevelApproverTitle + " note: "
                + safeText(request.getManagerApproverReason(), "No " + firstLevelApproverTitle.toLowerCase() + " note provided.")
                + "\nSuggested pay treatment: " + (Boolean.TRUE.equals(request.getSuggestedPaid()) ? "Paid" : "Unpaid")
                + portalFooter("Please log in to MaridOne to review and finalize this leave request.");

        sendAfterCommit(resolveHrEmails(), subject, body);
    }

    @Async
    public void sendLeaveForwardedToEmployee(LeaveRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String leaveDates = NotificationMessageFormatter.formatDateRange(
                safeDate(request.getStartDateTime()),
                safeDate(request.getEndDateTime())
        );
        String firstLevelApproverLabel = firstLevelApproverLabel(request.getEmployee());
        String firstLevelApproverTitle = firstLevelApproverTitle(request.getEmployee());
        String subject = "Your Leave Request Is Waiting for HR Review";
        String body = "Your leave request for " + leaveDates + " was endorsed by " + firstLevelApproverLabel + " and is now waiting for HR review."
                + "\n" + firstLevelApproverTitle + " note: "
                + safeText(request.getManagerApproverReason(), "No " + firstLevelApproverTitle.toLowerCase() + " note provided.");

        sendAfterCommit(resolveEmployeeEmails(request.getEmployee()), subject, body + portalFooter("Please log in to MaridOne for details."));
    }

    @Async
    public void sendLeaveManagerDecisionToEmployee(LeaveRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String leaveDates = NotificationMessageFormatter.formatDateRange(
                safeDate(request.getStartDateTime()),
                safeDate(request.getEndDateTime())
        );
        String firstLevelApproverLabel = firstLevelApproverLabel(request.getEmployee());
        String firstLevelApproverTitle = firstLevelApproverTitle(request.getEmployee());
        String subject = "Your Leave Request Was Rejected by " + firstLevelApproverTitle;
        String body = "Your leave request for " + leaveDates + " was rejected by " + firstLevelApproverLabel + "."
                + "\n" + firstLevelApproverTitle + " note: "
                + safeText(request.getApproverReason(), "No " + firstLevelApproverTitle.toLowerCase() + " note provided.");

        sendAfterCommit(resolveEmployeeEmails(request.getEmployee()), subject, body + portalFooter("Please log in to MaridOne for details."));
    }

    public void sendLeaveDecisionToEmployee(LeaveRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String leaveDates = NotificationMessageFormatter.formatDateRange(
                safeDate(request.getStartDateTime()),
                safeDate(request.getEndDateTime())
        );
        String subject;
        String body;
        if (request.getRequestStatus() == org.example.maridone.enums.Status.APPROVED) {
            subject = "Your Leave Request Was Approved";
            body = "Your leave request for " + leaveDates + " was approved as "
                    + (Boolean.TRUE.equals(request.getPaid()) ? "paid" : "unpaid") + " leave."
                    + "\nHR note: " + safeText(request.getApproverReason(), "No HR note provided.");
        } else {
            subject = "Your Leave Request Was Rejected";
            body = "Your leave request for " + leaveDates + " was rejected."
                    + "\nHR note: " + safeText(request.getApproverReason(), "No HR note provided.");
        }

        sendAfterCommit(resolveEmployeeEmails(request.getEmployee()), subject, body + portalFooter("Please log in to MaridOne for details."));
    }

    public void sendOvertimeSubmittedToManager(OvertimeRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        Employee manager = resolveManagerRecipient(request.getEmployee());
        if (manager == null) {
            return;
        }

        String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        String subject = "New Overtime Request Awaiting Manager Review";
        String body = employeeName + " submitted an overtime request for " + workDate + "."
                + "\nSchedule: " + request.getStartTime().toLocalTime() + " to " + request.getEndTime().toLocalTime()
                + "\nReason: " + safeText(request.getReason(), "No reason provided.")
                + portalFooter("Please log in to MaridOne to review this overtime request.");

        sendAfterCommit(resolveEmployeeEmails(manager), subject, body);
    }

    public void sendOvertimeSubmittedToManagement(OvertimeRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        String subject = "New Overtime Request Awaiting Management Review";
        String body = employeeName + " submitted an overtime request for " + workDate + "."
                + "\nSchedule: " + request.getStartTime().toLocalTime() + " to " + request.getEndTime().toLocalTime()
                + "\nReason: " + safeText(request.getReason(), "No reason provided.")
                + portalFooter("Please log in to MaridOne to review this overtime request.");

        sendAfterCommit(resolveManagementEmails(), subject, body);
    }

    public void sendOvertimeForwardedToHr(OvertimeRequest request) {
        if (request == null) {
            return;
        }

        String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        String firstLevelApproverTitle = firstLevelApproverTitle(request.getEmployee());
        String subject = "Overtime Request Ready for HR Review";
        String body = employeeName + "'s overtime request for " + workDate + " is ready for HR review."
                + "\nWork window: " + request.getStartTime().toLocalTime() + " to " + request.getEndTime().toLocalTime()
                + "\n" + firstLevelApproverTitle + " note: "
                + safeText(request.getManagerApproveReason(), "No " + firstLevelApproverTitle.toLowerCase() + " note provided.")
                + "\nSuggested pay treatment: " + (Boolean.TRUE.equals(request.getSuggestedPaid()) ? "Paid" : "Unpaid")
                + portalFooter("Please log in to MaridOne to review and finalize this overtime request.");

        sendAfterCommit(resolveHrEmails(), subject, body);
    }

    public void sendOvertimeForwardedToEmployee(OvertimeRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        String firstLevelApproverLabel = firstLevelApproverLabel(request.getEmployee());
        String firstLevelApproverTitle = firstLevelApproverTitle(request.getEmployee());
        String subject = "Your Overtime Request Is Waiting for HR Review";
        String body = "Your overtime request for " + workDate + " was endorsed by " + firstLevelApproverLabel + " and is now waiting for HR review."
                + "\n" + firstLevelApproverTitle + " note: "
                + safeText(request.getManagerApproveReason(), "No " + firstLevelApproverTitle.toLowerCase() + " note provided.");

        sendAfterCommit(resolveEmployeeEmails(request.getEmployee()), subject, body + portalFooter("Please log in to MaridOne for details."));
    }

    public void sendOvertimeManagerDecisionToEmployee(OvertimeRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        String firstLevelApproverLabel = firstLevelApproverLabel(request.getEmployee());
        String firstLevelApproverTitle = firstLevelApproverTitle(request.getEmployee());
        String subject = "Your Overtime Request Was Rejected by " + firstLevelApproverTitle;
        String body = "Your overtime request for " + workDate + " was rejected by " + firstLevelApproverLabel + "."
                + "\n" + firstLevelApproverTitle + " note: "
                + safeText(request.getApproveReason(), "No " + firstLevelApproverTitle.toLowerCase() + " note provided.");

        sendAfterCommit(resolveEmployeeEmails(request.getEmployee()), subject, body + portalFooter("Please log in to MaridOne for details."));
    }

    public void sendOvertimeDecisionToEmployee(OvertimeRequest request) {
        if (request == null || request.getEmployee() == null) {
            return;
        }

        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        String subject;
        String body;
        if (request.getRequestStatus() == org.example.maridone.enums.Status.APPROVED) {
            subject = "Your Overtime Request Was Approved";
            body = "Your overtime request for " + workDate + " was approved as "
                    + (Boolean.TRUE.equals(request.getPaid()) ? "paid" : "unpaid") + " overtime."
                    + "\nHR note: " + safeText(request.getApproveReason(), "No HR note provided.");
        } else {
            subject = "Your Overtime Request Was Rejected";
            body = "Your overtime request for " + workDate + " was rejected."
                    + "\nHR note: " + safeText(request.getApproveReason(), "No HR note provided.");
        }

        sendAfterCommit(resolveEmployeeEmails(request.getEmployee()), subject, body + portalFooter("Please log in to MaridOne for details."));
    }

    public void sendDisputeSubmittedToHr(DisputeRequest request) {
        if (request == null || request.getPayrollItem() == null) {
            return;
        }

        Employee employee = request.getPayrollItem().getEmployee();
        String employeeName = NotificationMessageFormatter.formatEmployeeName(employee);
        String payrollPeriod = NotificationMessageFormatter.formatPayrollPeriod(request.getPayrollItem().getPayrollRun());
        String subject = "New Payslip Dispute Submitted";
        String body = employeeName + " submitted a payslip dispute for " + payrollPeriod + "."
                + "\nSubject: " + safeText(request.getSubject(), "No subject provided.")
                + "\nReason: " + safeText(request.getReason(), "No reason provided.")
                + portalFooter("Please log in to MaridOne to review this dispute.");

        sendAfterCommit(resolveHrEmails(), subject, body);
    }

    public void sendDisputeDecisionToEmployee(DisputeRequest request) {
        if (request == null || request.getPayrollItem() == null || request.getPayrollItem().getEmployee() == null) {
            return;
        }

        String subject = request.getStatus() == org.example.maridone.enums.Status.APPROVED
                ? "Your Payslip Dispute Was Approved"
                : "Your Payslip Dispute Was Rejected";
        String body = "Your payslip dispute about \"" + safeText(request.getSubject(), "your payroll item") + "\" was "
                + safeText(request.getStatus() == null ? null : request.getStatus().name().toLowerCase(), "updated") + "."
                + "\nResolution note: " + safeText(request.getStatusReason(), "No resolution note provided.");

        sendAfterCommit(
                resolveEmployeeEmails(request.getPayrollItem().getEmployee()),
                subject,
                body + portalFooter("Please log in to MaridOne for details.")
        );
    }

    private Employee resolveManagerRecipient(Employee employee) {
        Employee manager = employee == null ? null : employee.getManager();
        if (manager == null || manager.getPosition() != Position.MANAGER || resolveEmployeeEmails(manager).isEmpty()) {
            return null;
        }
        return manager;
    }

    private Collection<String> resolveHrEmails() {
        return employeeRepository
                .findByPositionAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(Position.HR, EmploymentStatus.TERMINATED)
                .stream()
                .map(this::resolveEmployeeEmails)
                .flatMap(Collection::stream)
                .toList();
    }

    private Collection<String> resolveManagementEmails() {
        return employeeRepository
                .findByPositionAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(Position.MANAGEMENT, EmploymentStatus.TERMINATED)
                .stream()
                .map(this::resolveEmployeeEmails)
                .flatMap(Collection::stream)
                .toList();
    }

    private String firstLevelApproverLabel(Employee employee) {
        return isManagementFirstLevelReview(employee) ? "management" : "your manager";
    }

    private String firstLevelApproverTitle(Employee employee) {
        return isManagementFirstLevelReview(employee) ? "Management" : "Manager";
    }

    private boolean isManagementFirstLevelReview(Employee employee) {
        return employee != null && employee.getManager() == null;
    }

    private Collection<String> resolveEmployeeEmails(Employee employee) {
        if (employee == null) {
            return List.of();
        }

        return java.util.stream.Stream.of(employee.getCompanyEmail(), employee.getPersonalEmail())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @ExecutionTime
    private void sendAfterCommit(Collection<String> recipients, String subject, String body) {
        Set<String> emails = recipients == null
                ? Set.of()
                : recipients.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (emails.isEmpty() || !StringUtils.hasText(subject) || !StringUtils.hasText(body)) {
            return;
        }

        Runnable sendTask = () -> emails.forEach(email -> sendSafe(email, subject.trim(), body.trim()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAsync(sendTask).thenRun(() -> logger.info("processed email/s for {} recipient/s.", emails.size()));
                }
            });
            return;
        }
        runAsync(sendTask).thenRun(() -> logger.info("no synchronous task detected. processed email for {} recipient/s.", emails.size()));;
    }

    private void sendSafe(String email, String subject, String body) {
        try {
            emailService.sendPlainTextEmail(email, subject, body);
        } catch (Exception ex) {
            logger.warn("Unable to send workflow email to {} with subject '{}': {}", email, subject, ex.getMessage());
        }
    }

    private String portalFooter(String instruction) {
        return "\n\n" + instruction + "\n" + resolvePortalUrl();
    }

    private String resolvePortalUrl() {
        String configuredUrl = defaultConfig.getUrl().toString();
        return StringUtils.hasText(configuredUrl) ? configuredUrl.trim() : "http://localhost:5173";
    }

    private LocalDate safeDate(java.time.LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String formatLabel(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "Unspecified";
        }
        return java.util.Arrays.stream(rawValue.trim().toLowerCase().split("_"))
                .map(part -> part.isBlank() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(rawValue);
    }
}

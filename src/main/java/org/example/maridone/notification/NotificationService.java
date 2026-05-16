package org.example.maridone.notification;

import org.example.maridone.annotation.AutoScheduled;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.employee.EmployeeService;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.exception.notfound.RequestNotFoundException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.notification.spec.NotificationSpecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    public NotificationService(
            NotificationRepository notificationRepository,
            EmployeeRepository employeeRepository,
            EmployeeService employeeService) {

        this.notificationRepository = notificationRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public Page<Notification> getUserNotifications(String username, Boolean status, Pageable pageable) {
        Specification<Notification> spec = Specification.allOf(
                CommonSpecs.fieldEquals("readStatus", status),
                NotificationSpecs.hasUsername(username)
        );
        return notificationRepository.findAll(spec, pageable);
    }


    @Transactional
    @ExecutionTime
    public void readNotification(String username, Long notificationId) {
        if (notificationId == null) {
            throw new InvalidActionException("Notification ID is required.");
        }

        Notification notif = notificationRepository
                .findByNotificationIdAndEmployee_UserAccount_Username(notificationId, username)
                .orElseThrow(() -> new RequestNotFoundException("Notification", notificationId));

        notif.setReadStatus(true);
        notificationRepository.save(notif);
    }

    @ExecutionTime
    public void notifyUsers(List<Long> employeeIds, String message, String importance) {
        List<Notification> notifications = new ArrayList<>();
        for (Long employeeId :  employeeIds) {
            employeeRepository.findById(employeeId).ifPresentOrElse(emp -> {
                Notification notification = new Notification();
                notification.setEmployee(emp);
                notification.setReadStatus(false);
                notification.setImportance(importance);
                notification.setMessage(message);
                notifications.add(notification);
            }, () ->  logger.warn("Employee  {} not found. skipping...",  employeeId));

        }
        notificationRepository.saveAll(notifications);
    }

    public void notifyRole(String message, String importance, Position targetRole) {
        List<Long> employeeIds = employeeRepository.findEmployeeIdsByPosition(targetRole, EmploymentStatus.TERMINATED);
        notifyUsers(employeeIds, message, importance);
    }

    @Transactional
    @ExecutionTime
    public void notifyRun(List<Long> empIds, String message, Long payId) {
        List<Notification> notifications = new ArrayList<>();

        for (Long employeeId : empIds) {
            Employee emp = employeeRepository.getReferenceById(employeeId);
            Notification notification = new Notification();
            notification.setEmployee(emp);
            notification.setReadStatus(false);
            notification.setMessage(message);
            notification.setImportance("HIGH");
            notifications.add(notification);
        }
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    @AutoScheduled
    public void bulkClean(Duration days){
        Instant cutoff = Instant.now().minus(days);
        notificationRepository.bulkDelete(cutoff);
    }
}

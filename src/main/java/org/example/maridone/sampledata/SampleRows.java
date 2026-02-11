package org.example.maridone.sampledata;

import org.example.maridone.schedule.calendar.CalendarRepository;
import org.example.maridone.schedule.calendar.CompanyCalendar;
import org.example.maridone.schedule.shift.ShiftRepository;
import org.example.maridone.schedule.shift.ShiftSchedule;
import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.*;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.notification.Notification;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.itemcomponent.DeductionsLine;
import org.example.maridone.payroll.itemcomponent.EarningsLine;
import org.example.maridone.payroll.PayrollItem;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
public class SampleRows {

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    PayrollRunRepository payrollRunRepository;

    @Autowired
    CalendarRepository calendarRepository;

    @Autowired
    ShiftRepository shiftRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        sampleData();
    }

    public void sampleData() {

        List<Employee> employees = new ArrayList<>();
        List<PayrollItem> items = new ArrayList<>();
        PayrollRun payrollRun = new PayrollRun();
        CompanyCalendar companyEvent;
        CompanyCalendar companyEvent2;
        companyEvent = calendarEvents(new CompanyCalendar(), 1);
        companyEvent2 = calendarEvents(new CompanyCalendar(), 2);

        calendarRepository.save(companyEvent);
        calendarRepository.save(companyEvent2);


        payrollRun.setPayrollStatus(PayrollStatus.IN_PROGRESS);
        payrollRun.setRunType(RunType.REGULAR);
        payrollRun.setPeriodStart(LocalDate.now().minusDays(14));
        payrollRun.setPeriodEnd(LocalDate.now());
        payrollRun.setPeriodDescription("Test1");
        payrollRunRepository.save(payrollRun);

        for (int i = 1; i <= 10; i++) {
            Employee emp = new Employee();
            LocalDate birthDate = LocalDate.of(2000, 10, i+1);
            Address address = new Address();

            emp.setFirstName("firstName" + i);
            if (i == 5 || i == 8) {
                emp.setMiddleName("middleName" + i);
            }
            emp.setLastName("lastName" + (i*2));
            emp.setBirthDate(birthDate);
            emp.setEmploymentDateStart(LocalDate.now().minusDays(i*10));
            if (i == 4) {
                emp.setEmploymentDateEnd(LocalDate.now().minusDays(i));
            }
            switch(i) {
                case 1: {
                    emp.setEmploymentStatus(EmploymentStatus.REGULAR);
                    emp.setPosition(Position.MANAGEMENT);
                    break;
                }
                case 2: {
                    emp.setEmploymentStatus(EmploymentStatus.PART_TIME);
                    emp.setPosition(Position.HR);
                    break;
                }
                case 3: {
                    emp.setEmploymentStatus(EmploymentStatus.SUSPENDED);
                    emp.setPosition(Position.ACCOUNTING);
                    break;
                }
                case 4: {
                    emp.setEmploymentStatus(EmploymentStatus.TERMINATED);
                    emp.setPosition(Position.EMPLOYEE);
                    break;
                }
                default: {
                    emp.setEmploymentStatus(EmploymentStatus.REGULAR);
                    emp.setPosition(Position.EMPLOYEE);
                }
            }

            emp.setEmail("email" + i + "@gmail.com");
            emp.setPhoneNumber("0901500009" + i);

            address.setPermanentAddress("permanentAddress" + i);
            address.setCity("city" + i);
            address.setState("state" + i);
            address.setZipCode("174" + i);
            address.setCountry("Philippines");

            if (i == 4) {
                address.setTemporaryAddress("temporaryAddress" + i);
            }

            emp.setAddress(address);



            List<BankAccount> bankAccounts = createBankAccounts(emp, i);
            emp.setBankAccounts(bankAccounts);

            UserAccount userAccount = new UserAccount();
            userAccount.setAccountStatus(AccountStatus.ACTIVE);
            userAccount.setEmployee(emp);
            userAccount.setPasswordHash(passwordEncoder.encode("test"));
            userAccount.setUsername("userName" + i);
            emp.setUserAccount(userAccount);

            PayrollItem item = createPayrollItem(emp, i, payrollRun);
            items.add(item);
            emp.setPayrollItems(new ArrayList<PayrollItem>());
            emp.getPayrollItems().add(item);

            List<Notification> notifications = createNotifications(emp, i);
            emp.setNotifications(notifications);

            List<LeaveRequest> leaveRequests = createLeave(emp, i);
            emp.setRequests(leaveRequests);

            List<LeaveBalance> leaveBalance = createBalance(emp, i);
            emp.setLeaveBalance(leaveBalance);

            List<ShiftSchedule> schedule = employeeSchedule(emp, i);
            emp.setShifts(schedule);

            employeeRepository.save(emp);
        }

        payrollRun.setItems(items);
    }

    public static List<BankAccount> createBankAccounts(Employee emp, int i) {
        List<BankAccount> bankAccounts = new ArrayList<>();
        BankAccount bankAccount = new BankAccount();
        BankAccount bankAccount2 = new BankAccount();

        bankAccount.setBankName("BPI");
        bankAccount2.setBankName("BDO");

        bankAccount.setAccountNumber(String.valueOf(i));
        bankAccount2.setAccountNumber(String.valueOf(i*10));

        bankAccount.setEmployee(emp);
        bankAccount2.setEmployee(emp);

        if (i%3 == 0) {
            bankAccount.setActive(Boolean.FALSE);
        } else
            bankAccount.setActive(Boolean.TRUE);
        if (i % 6 == 0) {
            bankAccount2.setActive(Boolean.FALSE);
        } else
            bankAccount2.setActive(Boolean.TRUE);


        bankAccounts.add(bankAccount);
        bankAccounts.add(bankAccount2);



        return bankAccounts;
    }

    public static List<Notification> createNotifications(Employee emp, int i) {

        List<Notification> notifications = new ArrayList<>();
        for (int j = 0; j < 5; j++) {
            Notification notification = new Notification();
            notification.setMessage("Sample notification message " + j);
            notification.setCreatedAt(Instant.now().minus(i, ChronoUnit.DAYS));
            notification.setReadStatus(j % 2 == 0);
            if (j == 2)
                notification.setImportance("HIGH");
            else
                notification.setImportance("LOW");
            notification.setEmployee(emp);
            notifications.add(notification);
        }
        return notifications;
    }

    public static List<LeaveRequest> createLeave(Employee emp, int i) {
        List<LeaveRequest> leaveRequests = new ArrayList<>();
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(emp);


        leaveRequest.setStartDate(LocalDate.now().plusDays(i));
        leaveRequest.setEndDate(LocalDate.now().plusDays(i + 2));

        leaveRequest.setRequestStatus((i % 2 == 0 ? Status.APPROVED : Status.PENDING));


        leaveRequest.setReason("Vacation request #" + i);


        leaveRequests.add(leaveRequest);
        return leaveRequests;
    }

    public static List<LeaveBalance> createBalance(Employee emp, int i) {
        List<LeaveBalance> balances =  new ArrayList<>();
        LeaveType[] leaves = LeaveType.values();
        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setEmployee(emp);
        leaveBalance.setBalanceHours(new BigDecimal(i*24));
        leaveBalance.setLeaveType(leaves[i%7]);
        balances.add(leaveBalance);
        return balances;
    }

    public static PayrollItem createPayrollItem(Employee emp, int i, PayrollRun payrollRun) {
        PayrollItem payrollItem = new PayrollItem();

        payrollItem.setEmployee(emp);
        payrollItem.setPayrollRun(payrollRun);
        payrollItem.setGrossPay(BigDecimal.valueOf(5000));
        payrollItem.setNetPay(BigDecimal.valueOf(3000));
        payrollItem.setDeductions(deductionLines(payrollItem, 2));
        payrollItem.setEarnings(earningLines(payrollItem, 5));
        payrollItem.setDisputes(newDispute(i, payrollItem));

        return payrollItem;
    }

    public static List<DeductionsLine> deductionLines(PayrollItem item, int count) {
        List<DeductionsLine> deductions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DeductionsLine deduction = new DeductionsLine();
            deduction.setPayrollItem(item);
            deduction.setAmount(BigDecimal.valueOf(1000));
            if (i == 0)
                deduction.setDeductionType(DeductionType.SSS);
            else
                deduction.setDeductionType(DeductionType.PHILHEALTH);
            deductions.add(deduction);
        }

        return deductions;
    }

    public static List<EarningsLine> earningLines(PayrollItem item, int count) {
        List<EarningsLine> earnings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EarningsLine earning = new EarningsLine();
            earning.setPayrollItem(item);
            earning.setAmount(BigDecimal.valueOf(1000));
            earning.setEarningsDate(LocalDate.now().minusDays(i));
            earning.setHours(BigDecimal.valueOf(5+i));
            earning.setOvertime(false);
            earning.setRate(BigDecimal.valueOf(100));
            earnings.add(earning);
        }

        return earnings;
    }

    public static CompanyCalendar calendarEvents(CompanyCalendar calendar, int i) {
        calendar.setActive(true);

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        if (i == 1) {
            calendar.setStartDate(now.minusMonths(1).toInstant());
            calendar.setEndDate(now.minusMonths(1).plusDays(2).toInstant());
            calendar.setTitle("Teambuilding");
        } else {
            calendar.setStartDate(now.minusMonths(i).toInstant());
            calendar.setEndDate(now.minusMonths(i).plusDays(3).toInstant());
            calendar.setTitle("Another event: " + i);
        }

        return calendar;
    }

    public static List<ShiftSchedule> employeeSchedule(Employee emp, int i) {
        List<ShiftSchedule> shiftSchedules = new ArrayList<>();
        for (int k = 1; k <= 7; k++) {
            ShiftSchedule shiftSchedule = new ShiftSchedule();
            shiftSchedule.setEmployee(emp);
            shiftSchedule.setDayOfWeek(DayOfWeek.of(k));
            if (k == 1)
                shiftSchedule.setEarningsType(EarningsType.BASIC);
            else if(k == 2)
                shiftSchedule.setEarningsType(EarningsType.DOUBLE_OVERTIME);
            else
                shiftSchedule.setEarningsType(EarningsType.OVERTIME);
            shiftSchedule.setStartTime(LocalTime.now());
            shiftSchedule.setEndTime(LocalTime.now().plusHours(8));
            shiftSchedules.add(shiftSchedule);
        }
        return shiftSchedules;
    }

    public static List<DisputeRequest> newDispute(int num, PayrollItem item) {
        List<DisputeRequest> disputes = new ArrayList<>();
        DisputeRequest dispute = new DisputeRequest();

        dispute.setSubject("Subject of Dispute " + num);
        if (num % 2 == 0)
            dispute.setStatus(Status.PENDING);
        else if (num == 5)
            dispute.setStatus(Status.REJECTED);
        else
            dispute.setStatus(Status.APPROVED);
        dispute.setCreatedAt(Instant.now());
        dispute.setReason("Reason of Dispute " + num);
        dispute.setUpdatedAt(null);
        dispute.setStatusReason(null);
        dispute.setPayrollItem(item);

        disputes.add(dispute);
        return disputes;
    }
}


package org.example.maridone.core.employee;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.schedule.shift.ShiftSchedule;
import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.embeddable.Address;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.notification.Notification;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.payroll.PayrollItem;

import java.time.LocalDate;
import java.util.List;

@Table(name = "employee")
@Entity
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emp_id", unique = true, nullable = false)
    private Long employeeId;

    @NotBlank
    @Column(name="first_name", nullable = false, length = 30)
    @Size(max = 30)
    private String firstName;

    @Column(name = "middle_name",nullable = true, length = 30)
    @Size(max = 30)
    private String middleName;

    @NotBlank
    @Column(name="last_name", nullable = false,  length = 30)
    @Size(max = 30)
    private String lastName;

    @Past
    @Column(name ="birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private EmploymentStatus employmentStatus;

    @Column(name = "employment_date_start", nullable = false)
    private LocalDate employmentDateStart;

    @Column(name ="employment_date_end", nullable = true)
    private LocalDate employmentDateEnd;

    @NotBlank
    @Column(name = "email", nullable = false, length = 254)
    @Email
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "position", nullable = false)
    @Enumerated(EnumType.STRING)
    private Position position;

    @Embedded
    private Address address;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<BankAccount> bankAccounts;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Notification> notifications;

    @OneToMany(mappedBy = "employee", cascade = {CascadeType.MERGE, CascadeType.PERSIST}, fetch = FetchType.LAZY)
    private List<LeaveRequest> requests;

    @OneToMany(mappedBy = "employee", cascade = {CascadeType.MERGE, CascadeType.PERSIST}, fetch = FetchType.LAZY)
    private List<PayrollItem> payrollItems;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<OvertimeRequest> overtimeRequests;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ShiftSchedule> shifts;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<LeaveBalance> leaveBalance;

    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL)
    private UserAccount userAccount;


    public Long getEmployeeId() {
        return employeeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public LocalDate getEmploymentDateStart() {
        return employmentDateStart;
    }

    public void setEmploymentDateStart(LocalDate employmentDate) {
        this.employmentDateStart = employmentDate;
    }

    public LocalDate getEmploymentDateEnd() {
        return employmentDateEnd;
    }

    public void setEmploymentDateEnd(LocalDate employmentDateEnd) {
        this.employmentDateEnd = employmentDateEnd;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public List<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public void setBankAccounts(List<BankAccount> bankAccounts) {
        this.bankAccounts = bankAccounts;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

    public List<LeaveRequest> getRequests() {
        return requests;
    }

    public void setRequests(List<LeaveRequest> requests) {
        this.requests = requests;
    }

    public List<PayrollItem> getPayrollItems() {
        return payrollItems;
    }

    public void setPayrollItems(List<PayrollItem> payrollItems) {
        this.payrollItems = payrollItems;
    }

    public List<LeaveBalance> getLeaveBalance() {
        return leaveBalance;
    }

    public void setLeaveBalance(List<LeaveBalance> leaveBalance) {
        this.leaveBalance = leaveBalance;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public List<OvertimeRequest> getOvertimeRequests() {
        return overtimeRequests;
    }

    public void setOvertimeRequests(List<OvertimeRequest> overtimeRequests) {
        this.overtimeRequests = overtimeRequests;
    }


    public List<ShiftSchedule> getShifts() {
        return shifts;
    }

    public void setShifts(List<ShiftSchedule> shifts) {
        this.shifts = shifts;
    }
}

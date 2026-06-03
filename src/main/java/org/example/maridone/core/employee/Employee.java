package org.example.maridone.core.employee;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Gender;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.embeddable.Address;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "employment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private EmploymentStatus employmentStatus;

    @Column(name = "exemption_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExemptionStatus exemptionStatus;

    @Column(name = "employment_date_start", nullable = false)
    private LocalDate employmentDateStart;

    @Column(name ="employment_date_end", nullable = true)
    private LocalDate employmentDateEnd;

    @Column(name = "yearly_salary", nullable = false)
    private BigDecimal yearlySalary;

    @NotBlank
    @Column(name = "company_email", nullable = false, length = 254, unique = true)
    @Email
    private String companyEmail;

    @Column(name = "personal_email", unique = true, length = 254)
    @Email
    private String personalEmail;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "position", nullable = false)
    @Enumerated(EnumType.STRING)
    private Position position;

    @Column(name = "designation", length = 100)
    private String designation;

    @Embedded
    private Address address;

    @Column(name = "sss_number")
    private String sssNumber;

    @Column(name = "philhealth_number")
    private String philhealthNumber;

    @Column(name = "pagibig_number")
    private String pagibigNumber;

    @Column(name = "tin_number")
    private String tinNumber;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_relationship")
    private String emergencyContactRelationship;

    @Column(name = "emergency_contact_number")
    private String emergencyContactNumber;

    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserAccount userAccount;

    /*
        Position.MANAGER
            Head of People Operations
            Head of Procurement
            Production Supervisor
            Product Development Engineer
                they are the managers of empIds
        Position.MANAGEMENT
        Managing Director
            holds empIds of the titles above:
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "emp_title", nullable = true)
    private ManagerTitle empTitle;

    //who is this employee's manager(if any)
    //not necessarily manager; can be team lead, company CEO, etc.
    //as long as you're under this manager_id, it is your boss
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = true)
    private Employee manager;

    //who does this employee handle? (if any)
    @OneToMany(mappedBy = "manager")
    private List<Employee> handledEmployees = new ArrayList<>();

    public Employee getManager() {
        return manager;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }

    public List<Employee> getHandledEmployees() {
        return handledEmployees;
    }

    public void setHandledEmployees(List<Employee> handledEmployees) {
        this.handledEmployees = handledEmployees;
    }

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

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public ExemptionStatus getExemptionStatus() {
        return exemptionStatus;
    }

    public void setExemptionStatus(ExemptionStatus exemptionStatus) {
        this.exemptionStatus = exemptionStatus;
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

    public BigDecimal getYearlySalary() {
        return yearlySalary;
    }

    public void setYearlySalary(BigDecimal yearlySalary) {
        this.yearlySalary = yearlySalary;
    }

    public String getCompanyEmail() {
        return companyEmail;
    }

    public void setCompanyEmail(String companyEmail) {
        this.companyEmail = companyEmail;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean verified) {
        isVerified = verified;
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

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public ManagerTitle getEmpTitle() {
        return empTitle;
    }

    public void setEmpTitle(ManagerTitle empTitle) {
        this.empTitle = empTitle;
    }

    public String getSssNumber() {
        return sssNumber;
    }

    public void setSssNumber(String sssNumber) {
        this.sssNumber = sssNumber;
    }

    public String getPhilhealthNumber() {
        return philhealthNumber;
    }

    public void setPhilhealthNumber(String philhealthNumber) {
        this.philhealthNumber = philhealthNumber;
    }

    public String getPagibigNumber() {
        return pagibigNumber;
    }

    public void setPagibigNumber(String pagibigNumber) {
        this.pagibigNumber = pagibigNumber;
    }

    public String getTinNumber() {
        return tinNumber;
    }

    public void setTinNumber(String tinNumber) {
        this.tinNumber = tinNumber;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }

    public void setEmergencyContactRelationship(String emergencyContactRelationship) {
        this.emergencyContactRelationship = emergencyContactRelationship;
    }

    public String getEmergencyContactNumber() {
        return emergencyContactNumber;
    }

    public void setEmergencyContactNumber(String emergencyContactNumber) {
        this.emergencyContactNumber = emergencyContactNumber;
    }
}

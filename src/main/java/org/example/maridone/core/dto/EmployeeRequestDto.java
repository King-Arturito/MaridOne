package org.example.maridone.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.SeparationReason;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.HrUpdate;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeRequestDto {

    @NotBlank(groups = OnCreate.class, message = "First name is required.")
    private String firstName;
    private String middleName;
    @NotBlank(groups = OnCreate.class, message = "Last name is required.")
    private String lastName;
    @NotNull(groups = OnCreate.class, message = "Birth date is required.")
    private LocalDate birthDate;
    @NotNull(groups = HrUpdate.class)
    private EmploymentStatus employmentStatus;
    private ExemptionStatus exemptionStatus;
    private LocalDate employmentDateStart;
    private LocalDate employmentDateEnd;
    @NotBlank(groups = OnCreate.class, message = "Email is required.")
    @Email(message = "Email must be valid.")
    private String email;
    @NotBlank(groups = OnCreate.class, message = "Phone number is required.")
    @Pattern(regexp = "^\\D*(?:\\d\\D*){11}$", message = "Phone number must be exactly 11 digits.")
    private String phoneNumber;
    private Position position;
    private String department;
    private BigDecimal yearlySalary;
    private Long managerId;

    private Address address;

    private String sssNumber;
    private String philhealthNumber;
    private String pagibigNumber;
    private String tinNumber;

    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactNumber;
    private SeparationReason separationReason;
    private Boolean eligibleForRehire;
    private String separationNotes;

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

    public ExemptionStatus getExemptionStatus() {
        return exemptionStatus;
    }

    public void setExemptionStatus(ExemptionStatus exemptionStatus) {
        this.exemptionStatus = exemptionStatus;
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

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public BigDecimal getYearlySalary() {
        return yearlySalary;
    }

    public void setYearlySalary(BigDecimal yearlySalary) {
        this.yearlySalary = yearlySalary;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
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

    public SeparationReason getSeparationReason() {
        return separationReason;
    }

    public void setSeparationReason(SeparationReason separationReason) {
        this.separationReason = separationReason;
    }

    public Boolean getEligibleForRehire() {
        return eligibleForRehire;
    }

    public void setEligibleForRehire(Boolean eligibleForRehire) {
        this.eligibleForRehire = eligibleForRehire;
    }

    public String getSeparationNotes() {
        return separationNotes;
    }

    public void setSeparationNotes(String separationNotes) {
        this.separationNotes = separationNotes;
    }
}

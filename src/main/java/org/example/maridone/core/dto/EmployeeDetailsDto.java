package org.example.maridone.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.SeparationReason;

public class EmployeeDetailsDto {

    private Long id;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate birthDate;
    private EmploymentStatus employmentStatus;
    private ExemptionStatus exemptionStatus;
    private LocalDate employmentDateStart;
    private LocalDate employmentDateEnd;
    private BigDecimal yearlySalary;
    private String email;
    private String isVerified;
    private String phoneNumber;
    private Position position;
    private String department;
    private String userAccountName;
    private Long managerId;
    private String managerName;
    private String managerHeadLabel;
    private Address address;
    private SeparationReason separationReason;
    private Boolean eligibleForRehire;
    private String separationNotes;
    private LocalDate separationEffectiveDate;
    private String separationProcessedBy;

    private String sssNumber;
    private String philhealthNumber;
    private String pagibigNumber;
    private String tinNumber;

    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(String isVerified) {
        this.isVerified = isVerified;
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

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public String getManagerHeadLabel() {
        return managerHeadLabel;
    }

    public void setManagerHeadLabel(String managerHeadLabel) {
        this.managerHeadLabel = managerHeadLabel;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
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

    public LocalDate getSeparationEffectiveDate() {
        return separationEffectiveDate;
    }

    public void setSeparationEffectiveDate(LocalDate separationEffectiveDate) {
        this.separationEffectiveDate = separationEffectiveDate;
    }

    public String getSeparationProcessedBy() {
        return separationProcessedBy;
    }

    public void setSeparationProcessedBy(String separationProcessedBy) {
        this.separationProcessedBy = separationProcessedBy;
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

    public String getUserAccountName() {
        return userAccountName;
    }

    public void setUserAccountName(String userAccountName) {
        this.userAccountName = userAccountName;
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

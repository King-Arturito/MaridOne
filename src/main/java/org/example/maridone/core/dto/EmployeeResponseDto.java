package org.example.maridone.core.dto;
import java.time.LocalDate;
import java.util.List;

import org.example.maridone.enums.AccountStatus;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Gender;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.SeparationReason;
import org.example.maridone.schedule.dto.ShiftResponseDto;

public class EmployeeResponseDto {

    private Long id;
    private String firstName;
    private String middleName;
    private String lastName;
    private Gender gender;
    private EmploymentStatus employmentStatus;
    private ExemptionStatus exemptionStatus;
    private LocalDate employmentDateStart;
    private String companyEmail;
    private String personalEmail;
    private String phoneNumber;
    private Position position;
    private String department;
    private String managerName;
    private String managerHeadLabel;
    private String userAccountName;
    private AccountStatus accountStatus;
    private List<ShiftResponseDto> shiftSchedules = List.of();
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

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public void setEmploymentDateStart(LocalDate employmentDateStart) {
        this.employmentDateStart = employmentDateStart;
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

    public String getUserAccountName() {
        return userAccountName;
    }

    public void setUserAccountName(String userAccountName) {
        this.userAccountName = userAccountName;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public List<ShiftResponseDto> getShiftSchedules() {
        return shiftSchedules;
    }

    public void setShiftSchedules(List<ShiftResponseDto> shiftSchedules) {
        this.shiftSchedules = shiftSchedules;
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

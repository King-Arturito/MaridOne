package org.example.maridone.core.dto;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Position;

public class EmployeeResponseDto {

    private Long id;
    private String firstName;
    private String middleName;
    private String lastName;
    private EmploymentStatus employmentStatus;
    private ExemptionStatus exemptionStatus;
    private Position position;


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

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }
}

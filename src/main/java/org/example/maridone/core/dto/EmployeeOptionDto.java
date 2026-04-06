package org.example.maridone.core.dto;

import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;

public class EmployeeOptionDto {

    private Long id;
    private String name;
    private EmploymentStatus status;
    private Position position;
    private String department;

    public EmployeeOptionDto() {
    }

    public EmployeeOptionDto(Long id, String name, EmploymentStatus status, Position position, String department) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.position = position;
        this.department = department;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EmploymentStatus getStatus() {
        return status;
    }

    public void setStatus(EmploymentStatus status) {
        this.status = status;
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
}

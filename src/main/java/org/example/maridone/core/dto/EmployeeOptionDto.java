package org.example.maridone.core.dto;

import org.example.maridone.enums.EmploymentStatus;

public class EmployeeOptionDto {

    private Long id;
    private String name;
    private EmploymentStatus status;

    public EmployeeOptionDto() {
    }

    public EmployeeOptionDto(Long id, String name, EmploymentStatus status) {
        this.id = id;
        this.name = name;
        this.status = status;
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
}

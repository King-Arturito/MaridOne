package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class EmployeeNotFoundException extends BaseException {
    public EmployeeNotFoundException(Long id) {
        super(
                ErrorCode.EMPLOYEE_NOT_FOUND,
                id == null ? "Employee Id provided is null" : "Employee Id " + id + " not found");
    }
}

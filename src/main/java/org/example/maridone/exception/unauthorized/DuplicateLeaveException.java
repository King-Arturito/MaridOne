package org.example.maridone.exception;

import org.example.maridone.enums.LeaveType;

public class DuplicateLeaveException extends RuntimeException {
    public DuplicateLeaveException(Long empId, LeaveType leaveType) {
        super(
                "Duplicate Leave for Employee ID: " + empId + " and LeaveType: " + leaveType +
                        ". Update Employee's leave hours for type: " + leaveType + " instead."

        );
    }
}

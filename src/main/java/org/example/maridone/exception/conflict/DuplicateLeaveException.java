package org.example.maridone.exception.conflict;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.exception.BaseException;

public class DuplicateLeaveException extends BaseException {
    public DuplicateLeaveException(Long empId, LeaveType leaveType) {
        super(
                ErrorCode.DUPLICATE_ACCOUNT,
                "Duplicate Leave for Employee ID: " + empId + " and LeaveType: " + leaveType +
                        ". Update Employee's leave hours for type: " + leaveType + " instead."
        );
    }
}

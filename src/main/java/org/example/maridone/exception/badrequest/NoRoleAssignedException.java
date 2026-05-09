package org.example.maridone.exception.badrequest;


import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class NoRoleAssignedException extends BaseException {
    public NoRoleAssignedException(String message) {
        super(
                ErrorCode.NO_ROLE_ASSIGNED,
                message
        );
    }
}

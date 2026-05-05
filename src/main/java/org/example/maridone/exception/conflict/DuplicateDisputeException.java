package org.example.maridone.exception.conflict;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class DuplicateDisputeException extends BaseException {
    public DuplicateDisputeException(Long itemId) {
        super(
                ErrorCode.DUPLICATE_DISPUTE,
                "Your dispute for Payslip ID: " + itemId + " is still pending.");
    }
}

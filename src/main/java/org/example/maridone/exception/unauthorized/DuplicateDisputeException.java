package org.example.maridone.exception;

public class DuplicateDisputeException extends RuntimeException {
    public DuplicateDisputeException(Long itemId, String message) {
        super("Your dispute for Payslip ID: " + itemId + " is still pending.");
    }
}

package org.example.maridone.exception.badrequest;


public class NoRoleAssignedException extends RuntimeException{
    public NoRoleAssignedException(String message) {
        super(message);
    }
}

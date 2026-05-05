package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class RequestNotFoundException extends BaseException {
    public RequestNotFoundException(String requestType, Long id) {

        super(
                ErrorCode.REQUEST_NOT_FOUND,
                requestType + " ID: "+ id + " not found.");
    }
}

package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class DocumentNotFound extends BaseException {
    public DocumentNotFound(String message) {
        super(
                ErrorCode.DOCUMENT_NOT_FOUND,
                message);
    }
}

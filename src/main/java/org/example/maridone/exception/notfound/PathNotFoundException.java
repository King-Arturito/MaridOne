package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class PathNotFoundException extends BaseException {
    public PathNotFoundException(Long pathId) {
        super(
                ErrorCode.PATH_NOT_FOUND,
                "Document with id: " + pathId + " not found");
    }
}

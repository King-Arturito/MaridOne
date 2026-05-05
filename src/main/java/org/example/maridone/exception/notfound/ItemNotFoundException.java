package org.example.maridone.exception.notfound;

import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.BaseException;

public class ItemNotFoundException extends BaseException {
    public ItemNotFoundException(Long id) {
        super(
                ErrorCode.ITEM_NOT_FOUND,
                "Item with ID " + id + " not found");
    }
}

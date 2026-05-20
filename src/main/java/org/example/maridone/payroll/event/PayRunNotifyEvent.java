package org.example.maridone.payroll.event;

import java.util.List;

public record PayRunNotifyEvent(
        List<Long> employeeIds,
        String message,
        Long payId){
}

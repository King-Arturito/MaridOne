package org.example.maridone.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LeaveUpdaterTask {

    /*
        to do:
            modify @Scheduled to the start of each year (jan 1, 2026/2027/......)
            must call LeaveService and you should create a method in LeaveService
            should add x leave balance hours to regular employee's vacation leave and sick leave
                refer to config/PayrollProperties for default value of x
                employee must be a regular employee (6 months)
                verify with handbook if balance hours stack with one another or resets every year
                    leaveHours += x; vs leaveHours = x
           keep return value as void for now
    */
    @Scheduled()
    public void addLeaveBalance() {
    }
}

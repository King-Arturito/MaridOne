package org.example.maridone.task;

import org.example.maridone.leave.LeaveService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BalanceUpdaterTask {
    private final LeaveService leaveService;

    public BalanceUpdaterTask(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    //Scheduled at January 1 of every year
    //scheduled date according to Handbook
    @Scheduled(cron = "0 0 0 1 1 *", zone = "Asia/Manila")
    public void addLeaveBalance() {
        leaveService.updateYearlyBalance();
    }
}

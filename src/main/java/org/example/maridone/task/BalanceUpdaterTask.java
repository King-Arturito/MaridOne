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

    @Scheduled(cron = "0 0 0 1 1 *")
    public void addLeaveBalance() {
        leaveService.updateYearlyBalance();
    }
}

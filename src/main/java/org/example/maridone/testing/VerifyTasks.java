package org.example.maridone.testing;

import org.example.maridone.payroll.PayrollService;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.task.BalanceUpdaterTask;
import org.example.maridone.task.CleanupTask;
import org.example.maridone.task.ShiftGeneratorTask;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Profile("dev")
public class VerifyTasks {

    private final ShiftGeneratorTask shiftGeneratorTask;
    private final BalanceUpdaterTask balanceUpdaterTask;
    private final CleanupTask cleanupTask;
    private final PayrollService payrollService;

    public VerifyTasks(ShiftGeneratorTask shiftGeneratorTask,
                       BalanceUpdaterTask balanceUpdaterTask,
                       CleanupTask cleanupTask,
                       PayrollService payrollService) {
        this.shiftGeneratorTask = shiftGeneratorTask;
        this.balanceUpdaterTask = balanceUpdaterTask;
        this.cleanupTask = cleanupTask;
        this.payrollService = payrollService;
    }

    @PostMapping("/shift_generate")
    public void generateDailyShifts() {
        shiftGeneratorTask.generateDailyShifts();
    }

    @PostMapping("/balance_updater")
    public void updateBalance() {
        balanceUpdaterTask.addLeaveBalance();
    }

    @PostMapping("/payroll-run")
    public void run(@RequestBody RunCreateDto payload) {
        payrollService.processPayroll(payload);
    }

    @PostMapping("/clean-notification")
    public void cleanup() {
        cleanupTask.cleanNotification();
    }

}

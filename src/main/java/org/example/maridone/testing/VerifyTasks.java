package org.example.maridone.testing;

import org.example.maridone.task.BalanceUpdaterTask;
import org.example.maridone.task.RegularizationTask;
import org.example.maridone.task.ShiftGeneratorTask;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@Profile("dev")
public class VerifyTasks {

    private final ShiftGeneratorTask shiftGeneratorTask;
    private final RegularizationTask regularizationTask;
    private final BalanceUpdaterTask balanceUpdaterTask;

    public VerifyTasks(ShiftGeneratorTask shiftGeneratorTask,
                       RegularizationTask regularizationTask,
                       BalanceUpdaterTask balanceUpdaterTask) {
        this.shiftGeneratorTask = shiftGeneratorTask;
        this.regularizationTask = regularizationTask;
        this.balanceUpdaterTask = balanceUpdaterTask;
    }

    @PostMapping("/shift_generate")
    public void generateDailyShifts() {
        shiftGeneratorTask.generateDailyShifts();
    }

    @PostMapping("/balance_updater")
    public void updateBalance() {
        balanceUpdaterTask.addLeaveBalance();
    }

    @PostMapping("/regularization")
    public void updateRegularization() {
        regularizationTask.regularize();
    }
}

package org.example.maridone.task;

import org.example.maridone.schedule.shift.ShiftService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShiftGeneratorTask {

    private final ShiftService shiftService;

    public ShiftGeneratorTask(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    //Scheduled at 00:00 everyday
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Manila")
    @Transactional
    public void generateDailyShifts() {
        shiftService.setDailyShifts();
    }

}

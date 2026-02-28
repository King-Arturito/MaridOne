package org.example.maridone;

import org.example.maridone.task.ShiftGeneratorTask;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class testingONLY {

    private final ShiftGeneratorTask shiftGeneratorTask;

    public testingONLY(ShiftGeneratorTask shiftGeneratorTask) {
        this.shiftGeneratorTask = shiftGeneratorTask;
    }

    @PostMapping("/shift_generate")
    public void generateDailyShifts() {
        shiftGeneratorTask.generateDailyShifts();
    }
}

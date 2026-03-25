package org.example.maridone.component;

import org.example.maridone.overtime.OvertimeRequestRepository;
import org.springframework.stereotype.Component;

@Component("overtimeOwnerCheck")
public class OvertimeOwnerCheck implements CheckerInterface{

    private final OvertimeRequestRepository overtimeRequestRepository;

    public OvertimeOwnerCheck(OvertimeRequestRepository overtimeRequestRepository) {
        this.overtimeRequestRepository = overtimeRequestRepository;
    }
    @Override
    public boolean isSelf(Long overtimeId, String username) {
        return overtimeRequestRepository.isOvertimeOwnedByUser(overtimeId, username);
    }
}

package org.example.maridone.component;


import org.example.maridone.leave.request.LeaveRequestRepository;
import org.springframework.stereotype.Component;

@Component("leaveOwnerCheck")
public class LeaveOwnerCheck implements CheckerInterface{

    private final LeaveRequestRepository leaveRequestRepository;
    public LeaveOwnerCheck(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Override
    public boolean isSelf(Long leaveRequestId, String username) {
        return leaveRequestRepository.isLeaveOwnedByUser(leaveRequestId, username);
    }
}

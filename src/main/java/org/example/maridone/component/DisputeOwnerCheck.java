package org.example.maridone.component;

import org.example.maridone.payroll.dispute.DisputeRequestRepository;
import org.springframework.stereotype.Component;

@Component("disputeOwnerCheck")
public class DisputeOwnerCheck implements CheckerInterface{

    private final DisputeRequestRepository disputeRequestRepository;

    public DisputeOwnerCheck(DisputeRequestRepository disputeRequestRepository) {
        this.disputeRequestRepository = disputeRequestRepository;
    }
    @Override
    public boolean isSelf(Long disputeId, String username) {
        return disputeRequestRepository.isDisputeOwnedByUser(disputeId, username);
    }
}

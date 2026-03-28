package org.example.maridone.component;

import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.springframework.stereotype.Component;

@Component("itemOwnerCheck")
public class ItemOwnerCheck implements CheckerInterface {

    private final PayrollItemRepository payrollItemRepository;
    private final UserAccountRepository userAccountRepository;

    public ItemOwnerCheck(PayrollItemRepository payrollItemRepository, UserAccountRepository userAccountRepository) {
        this.payrollItemRepository = payrollItemRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean isSelf(Long itemId, String username) {
        UserAccount user = userAccountRepository.findByUsername(username).orElse(null);
        if  (user == null || user.getEmployee() == null) {
            return false;
        }
        Long userId = user.getEmployee().getEmployeeId();
        return payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
                        itemId,
                        PayrollStatus.POSTED,
                        RunType.ADJUSTMENT
                )
                .map(item -> {
                    if (item.getEmployee() == null) {
                        return false;
                    }
                    return item.getEmployee().getEmployeeId().equals(userId);
                })
                .orElse(false);
    }

}

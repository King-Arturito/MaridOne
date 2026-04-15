package org.example.maridone.component;

import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.DocumentPathRepository;
import org.example.maridone.enums.Position;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("documentOwnerCheck")
public class DocumentOwnerCheck implements CheckerInterface {

    private final DocumentPathRepository documentPathRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;

    public DocumentOwnerCheck(DocumentPathRepository documentPathRepository,
                              UserAccountRepository userAccountRepository,
                              EmployeeRepository employeeRepository) {
        this.documentPathRepository = documentPathRepository;
        this.userAccountRepository = userAccountRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public boolean isSelf(Long pathId, String username) {
        String dpUsername = documentPathRepository.findUsernameByPathId(pathId).orElse(null);
        if (dpUsername == null) {
            return false;
        }
        if (dpUsername.equals(username)) {
            return true;
        }

        UserAccount viewer = userAccountRepository.findByUsername(username).orElse(null);
        if (viewer == null || viewer.getEmployee() == null) {
            return false;
        }

        Position viewerPosition = viewer.getEmployee().getPosition();
        if (viewerPosition == Position.HR || viewerPosition == Position.MANAGEMENT) {
            return true;
        }

        if (viewerPosition != Position.MANAGER) {
            return false;
        }

        Long managerEmployeeId = viewer.getEmployeeId();
        Long ownerEmployeeId = userAccountRepository.findEmployeeIdByUsername(dpUsername);
        if (managerEmployeeId == null || ownerEmployeeId == null) {
            return false;
        }

        List<Long> handledEmployeeIds = employeeRepository.findHandledEmployeeIds(managerEmployeeId);
        return handledEmployeeIds.contains(ownerEmployeeId);
    }
}

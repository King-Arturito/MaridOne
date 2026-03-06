package org.example.maridone.component;

import org.example.maridone.document.DocumentPathRepository;
import org.springframework.stereotype.Component;

@Component("documentOwnerCheck")
public class DocumentOwnerCheck implements CheckerInterface {

    private final DocumentPathRepository documentPathRepository;

    public DocumentOwnerCheck(DocumentPathRepository documentPathRepository) {
        this.documentPathRepository = documentPathRepository;
    }

    @Override
    public boolean isSelf(Long pathId, String username) {
        String dpUsername = documentPathRepository.findUsernameByPathId(pathId).orElse(null);
        if (dpUsername == null) {
            return false;
        }
        return dpUsername.equals(username);
    }
}

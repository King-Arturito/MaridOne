package org.example.maridone.document.spec;

import org.example.maridone.document.path.DocumentPath;
import org.springframework.data.jpa.domain.Specification;

public class DocumentSpecs {
    public static Specification<DocumentPath> hasUsername(String username) {

        return (root, query, cb) ->  {
            if(username == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("username").get("username"), username);
        };
    }
}

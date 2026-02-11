package org.example.maridone.notification.spec;

import jakarta.persistence.criteria.JoinType;
import org.example.maridone.notification.Notification;
import org.springframework.data.jpa.domain.Specification;

public class NotificationSpecs {

    public static Specification<Notification> hasUsername(String username) {
        return (root, query, cb) -> {
            if (username == null) {
                return cb.conjunction();
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root
                        .fetch("employee", JoinType.LEFT)
                        .fetch("userAccount", JoinType.LEFT);
            }
            return cb.equal(
                    root
                            .join("employee")
                            .join("userAccount")
                            .get("username"), username);
        };
    }
}

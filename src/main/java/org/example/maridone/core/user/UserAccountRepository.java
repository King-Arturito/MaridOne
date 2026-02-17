package org.example.maridone.core.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount,Long> {
    Optional<UserAccount> findByUsername(String username);

    Boolean existsByEmployee_EmployeeId(Long employeeId);

    boolean existsByUsername(String username);
}

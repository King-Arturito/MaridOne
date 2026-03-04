package org.example.maridone.core.user;

import org.example.maridone.enums.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount,Long> {
    Optional<UserAccount> findByUsername(String username);

    Boolean existsByEmployee_EmployeeId(Long employeeId);

    boolean existsByUsername(String username);

    @Query("select u.employee.position from UserAccount u where u.username = :username")
    Optional<Position> findPositionByUsername(@Param("username") String username);
}

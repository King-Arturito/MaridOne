package org.example.maridone.core.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeSeparationRepository extends JpaRepository<EmployeeSeparation, Long> {

    Optional<EmployeeSeparation> findTopByEmployee_EmployeeIdOrderByProcessedAtDescSeparationIdDesc(Long employeeId);
}

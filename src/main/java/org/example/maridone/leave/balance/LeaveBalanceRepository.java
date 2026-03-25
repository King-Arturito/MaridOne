package org.example.maridone.leave.balance;

import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance,Long>, JpaSpecificationExecutor<LeaveBalance> {

    List<LeaveBalance> findByEmployee_EmployeeId(Long employeeId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE LeaveBalance lb SET lb.balanceHours = :newHours "+
            "WHERE lb.leaveType IN :leaveTypes " +
            "AND lb.employee.employeeId IN (SELECT e.employeeId FROM Employee e WHERE e.employmentStatus NOT IN :status)")
    int updateActiveEmployeeLeaveBalances(
            @Param("newHours") BigDecimal newHours,
            @Param("leaveTypes") List<LeaveType> leaveTypes,
            @Param("status") List<EmploymentStatus> status
    );


    Optional<LeaveBalance> findByLeaveTypeAndEmployee_EmployeeId(LeaveType leaveType, Long empId);
}

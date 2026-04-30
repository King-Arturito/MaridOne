package org.example.maridone.payroll.mapper;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.dto.*;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EmployerContributionLine;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.RunResponseDto;
import org.example.maridone.payroll.run.PayrollRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PayrollMapper {

    @Mapping(source = "payrollRun", target = "runDetails")
    @Mapping(source = "itemId", target = "id")
    ItemDetailsDto toItemDetailsDto(PayrollItem payrollItem);

    @Mapping(source = "payId", target = "id")
    RunResponseDto toRunDetailsDto(PayrollRun payrollRun);

    List<ItemDetailsDto> toItemDetailsDtos(List<PayrollItem> payrollItems);

    @Mapping(source = "earningsId", target = "earningsId")
    EarningsDto toEarningsDto(EarningsLine earningsLine);

    List<EarningsDto> toEarningsDtos(List<EarningsLine> earningsLines);

    @Mapping(source = "deductionsId", target = "deductionsId")
    DeductionsDto toDeductionsDto(DeductionsLine deductionsLine);

    List<DeductionsDto> toDeductionsDtos(List<DeductionsLine> deductionsLines);

    @Mapping(source = "employerContributionId", target = "employerContributionId")
    EmployerContributionDto toEmployerContributionDto(EmployerContributionLine employerContributionLine);

    @Mapping(source = "disputeId", target = "id")
    @Mapping(source = "payrollItem.itemId", target = "itemId")
    @Mapping(source = "payrollItem.employee.employeeId", target = "employeeId")
    @Mapping(source = "payrollItem.employee", target = "employeeName", qualifiedByName = "employeeToFullName")
    DisputeResponseDto toResponseDto(DisputeRequest disputeRequest);

    List<DisputeResponseDto> toResponseDtos(List<DisputeRequest> disputeRequests);

    @Mapping(source = "payrollRun.payId", target = "payId")
    @Mapping(source = "employee.employeeId", target ="empId")
    PayrollItemDto toPayrollItemDto(PayrollItem payrollItem);

    @Mapping(source = "itemId", target = "itemId")
    ItemSummaryDto toItemSummaryDto(PayrollItem payrollItem);

    List<ItemSummaryDto> toItemSummaryDtos(List<PayrollItem> payrollItems);

    @Named("employeeToFullName")
    default String employeeToFullName(Employee employee) {
        if (employee == null) {
            return null;
        }
        String safeFirstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String safeLastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (safeFirstName + " " + safeLastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }

}

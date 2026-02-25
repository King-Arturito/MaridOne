package org.example.maridone.payroll.mapper;

import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.dto.*;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.RunResponseDto;
import org.example.maridone.payroll.run.PayrollRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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

    @Mapping(source = "disputeId", target = "id")
    @Mapping(source = "payrollItem.itemId", target = "itemId")
    DisputeResponseDto toResponseDto(DisputeRequest disputeRequest);

    List<DisputeResponseDto> toResponseDtos(List<DisputeRequest> disputeRequests);

    @Mapping(source = "payrollRun.payId", target = "payId")
    @Mapping(source = "employee.employeeId", target ="empId")
    PayrollItemDto toPayrollItemDto(PayrollItem payrollItem);

    @Mapping(source = "itemId", target = "itemId")
    ItemSummaryDto toItemSummaryDto(PayrollItem payrollItem);

    List<ItemSummaryDto> toItemSummaryDtos(List<PayrollItem> payrollItems);

}

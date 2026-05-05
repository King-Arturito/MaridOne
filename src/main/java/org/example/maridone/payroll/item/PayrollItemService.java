package org.example.maridone.payroll.item;

import jakarta.validation.Valid;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonCalculator;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.Activity;
import org.example.maridone.enums.AdjustmentKind;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.PaySummaryKind;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.exception.notfound.RunNotFoundException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.log.ActivityLogRepository;
import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.payroll.dto.CorrectionSourceItemDto;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.dto.EmployerContributionDto;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.ItemViewDto;
import org.example.maridone.payroll.dto.PayTypeSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.PayrollLineCorrectionDto;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.DeductionsService;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.item.component.EarningsService;
import org.example.maridone.payroll.item.component.EmployerContributionRepository;
import org.example.maridone.payroll.item.component.EmployerContributionService;
import org.example.maridone.payroll.item.component.PayrollItemTotalsService;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PayrollItemService {
    private final PayrollItemRepository payrollItemRepository;
    private final PayrollMapper payrollMapper;
    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRepository employeeRepository;
    private final DeductionsRepository deductionsRepository;
    private final EarningsRepository earningsRepository;
    private final EarningsService earningsService;
    private final DeductionsService deductionsService;
    private final EmployerContributionRepository employerContributionRepository;
    private final EmployerContributionService employerContributionService;
    private final ActivityLogRepository activityLogRepository;
    private final PayrollItemTotalsService payrollItemTotalsService;

    public PayrollItemService(
            PayrollItemRepository payrollItemRepository,
            PayrollMapper payrollMapper,
            PayrollRunRepository payrollRunRepository,
            EmployeeRepository employeeRepository,
            DeductionsRepository deductionsRepository,
            EarningsRepository earningsRepository,
            EarningsService earningsService,
            DeductionsService deductionsService,
            EmployerContributionRepository employerContributionRepository,
            EmployerContributionService employerContributionService,
            ActivityLogRepository activityLogRepository,
            PayrollItemTotalsService payrollItemTotalsService
    ) {
        this.payrollItemRepository = payrollItemRepository;
        this.payrollMapper = payrollMapper;
        this.payrollRunRepository = payrollRunRepository;
        this.employeeRepository = employeeRepository;
        this.deductionsRepository = deductionsRepository;
        this.earningsRepository = earningsRepository;
        this.earningsService = earningsService;
        this.deductionsService = deductionsService;
        this.employerContributionRepository = employerContributionRepository;
        this.employerContributionService = employerContributionService;
        this.activityLogRepository = activityLogRepository;
        this.payrollItemTotalsService = payrollItemTotalsService;
    }

    @Transactional
    @ExecutionTime
    public List<ItemDetailsDto> getItems(Long empId) {
        List<PayrollItem> items = payrollItemRepository
                .findByEmployee_EmployeeIdAndPayrollRun_PayrollStatusOrderByPayrollRun_PeriodEndDescItemIdDesc(
                        empId,
                        PayrollStatus.POSTED
                );
        return items.stream()
                .map(this::toDisplayedItemDetails)
                .toList();
    }

    @Transactional
    @ExecutionTime
    public ItemDetailsDto createItem(@Valid PayrollItemDto payload) {
        PayrollItem item = new PayrollItem();
        PayrollRun run = payrollRunRepository.findById(payload.getPayId()).orElseThrow(
                () -> new RunNotFoundException("Payroll Run ID:" + payload.getPayId() + " not found.")
        );
        assertRunIsEditable(run);
        payrollItemRepository.findByPayrollRun_PayIdAndEmployee_EmployeeId(payload.getPayId(), payload.getEmpId())
                .ifPresent(existing -> {
                    throw new InvalidActionException("This employee already has a payroll item in the selected payroll run.");
                });

        Employee emp = employeeRepository.findById(payload.getEmpId()).orElseThrow(
                () -> new EmployeeNotFoundException(payload.getEmpId())
        );

        item.setPayrollRun(run);
        item.setEmployee(emp);
        item.setGrossPay(BigDecimal.ZERO.setScale(2));
        item.setNetPay(BigDecimal.ZERO.setScale(2));
        payrollItemRepository.save(item);
        return payrollMapper.toItemDetailsDto(item);
    }

    @ExecutionTime
    public Page<ItemViewDto> getRunItems(Long payId, ItemFilter filter, Pageable pageable) {
        PayrollRun run = payrollRunRepository.findById(payId).orElseThrow(
                () -> new RunNotFoundException("Payroll Run ID:" + payId + " not found.")
        );
        Page<ItemViewDto> items = payrollItemRepository.findSpecificDetails(payId, filter, pageable);
        if (run.getPayrollStatus() != PayrollStatus.POSTED || run.getRunType() == RunType.ADJUSTMENT) {
            List<ItemViewDto> itemViews = items.getContent().stream()
                    .map(this::withEmployerContributionTotal)
                    .toList();
            return new PageImpl<>(itemViews, pageable, items.getTotalElements());
        }

        List<ItemViewDto> displayedItems = items.getContent().stream()
                .map(this::toDisplayedItemView)
                .toList();
        return new PageImpl<>(displayedItems, pageable, items.getTotalElements());
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public CorrectionSourceItemDto getItemForCorrection(Long itemId) {
        PayrollItem item = payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
                        itemId,
                        PayrollStatus.POSTED,
                        RunType.ADJUSTMENT
                )
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        return toCorrectionSourceItemDto(item);
    }

    @Transactional
    @ExecutionTime
    public ItemDetailsDto addLineCorrection(Long payId, @Valid PayrollLineCorrectionDto payload) {
        PayrollRun targetRun = getEditableAdjustmentRun(payId);
        CorrectionSource source = resolveCorrectionSource(payload);
        BigDecimal correctedAmount = normalizeCorrectedAmount(payload.getCorrectedAmount());

        if (correctedAmount.compareTo(source.sourceAmount()) == 0) {
            throw new InvalidActionException("The corrected amount matches the original line, so no adjustment is needed.");
        }

        PayrollItem adjustmentItem = getOrCreateAdjustmentItem(targetRun, source.employee());
        stageLineCorrection(
                targetRun,
                adjustmentItem,
                source,
                correctedAmount
        );

        payrollItemTotalsService.refreshItem(adjustmentItem);
        payrollItemRepository.save(adjustmentItem);
        logLineCorrectionStaged(targetRun, source, correctedAmount);
        return payrollMapper.toItemDetailsDto(adjustmentItem);
    }

    public List<PayTypeSummaryDto> getSummarizedRunItems(Long payId) {
        List<PayTypeSummaryDto> summaryDtos = new ArrayList<>();

        deductionsRepository.getSummarizedDeductions(payId).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(PaySummaryKind.DEDUCTION, p.getType(), p.getTotalAmount()))
        );

        earningsRepository.getSummarizedEarnings(payId).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(PaySummaryKind.EARNING, p.getType(), p.getTotalAmount()))
        );

        employerContributionRepository.getSummarizedEmployerContributions(payId).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(PaySummaryKind.EMPLOYER_CONTRIBUTION, p.getType(), p.getTotalAmount()))
        );

        return summaryDtos;
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public Page<ItemViewDto> getReportItemsByDateRange(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            ItemFilter filter,
            Pageable pageable
    ) {
        Page<ItemViewDto> items = payrollItemRepository.findSpecificDetailsByDateRange(startDate, endDate, filter, pageable);
        List<ItemViewDto> displayedItems = items.getContent().stream()
                .map(this::toDisplayedItemView)
                .toList();
        return new PageImpl<>(displayedItems, pageable, items.getTotalElements());
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<PayTypeSummaryDto> getSummarizedReportItemsByDateRange(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        List<PayTypeSummaryDto> summaryDtos = new ArrayList<>();

        deductionsRepository.getSummarizedDeductionsByDateRange(startDate, endDate).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(PaySummaryKind.DEDUCTION, p.getType(), p.getTotalAmount()))
        );

        earningsRepository.getSummarizedEarningsByDateRange(startDate, endDate).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(PaySummaryKind.EARNING, p.getType(), p.getTotalAmount()))
        );

        employerContributionRepository.getSummarizedEmployerContributionsByDateRange(startDate, endDate).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(PaySummaryKind.EMPLOYER_CONTRIBUTION, p.getType(), p.getTotalAmount()))
        );

        return summaryDtos;
    }

    private PayrollRun getEditableAdjustmentRun(Long payId) {
        PayrollRun run = payrollRunRepository.findById(payId).orElseThrow(
                () -> new RunNotFoundException("Payroll Run ID:" + payId + " not found.")
        );
        if (run.getRunType() != RunType.ADJUSTMENT) {
            throw new InvalidActionException("Manual adjustments can only be added to adjustment payroll runs.");
        }
        assertRunIsEditable(run);
        return run;
    }

    private BigDecimal normalizeCorrectedAmount(BigDecimal correctedAmount) {
        BigDecimal normalizedAmount = CommonCalculator.normalize(correctedAmount);
        if (normalizedAmount.compareTo(BigDecimal.ZERO.setScale(2)) < 0) {
            throw new InvalidActionException("Corrected amount cannot be negative.");
        }
        return normalizedAmount;
    }

    private PayrollItem getOrCreateAdjustmentItem(PayrollRun targetRun, Employee employee) {
        return payrollItemRepository.findByPayrollRun_PayIdAndEmployee_EmployeeId(
                        targetRun.getPayId(),
                        employee.getEmployeeId()
                )
                .orElseGet(() -> {
                    PayrollItem item = new PayrollItem();
                    item.setPayrollRun(targetRun);
                    item.setEmployee(employee);
                    item.setGrossPay(BigDecimal.ZERO.setScale(2));
                    item.setNetPay(BigDecimal.ZERO.setScale(2));
                    item.setEarnings(new ArrayList<>());
                    item.setDeductions(new ArrayList<>());
                    return payrollItemRepository.save(item);
                });
    }

    private void stageLineCorrection(
            PayrollRun targetRun,
            PayrollItem adjustmentItem,
            CorrectionSource source,
            BigDecimal correctedAmount
    ) {
        BigDecimal netAdjustment = calculateNetAdjustment(source, correctedAmount);
        BigDecimal stagedAmount = CommonCalculator.normalize(netAdjustment.abs());

        if (netAdjustment.compareTo(BigDecimal.ZERO.setScale(2)) == 0) {
            throw new InvalidActionException("The corrected amount matches the original line, so no adjustment is needed.");
        }

        AdjustmentKind targetLineType = netAdjustment.signum() > 0 ? AdjustmentKind.EARNING : AdjustmentKind.DEDUCTION;
        if (targetLineType == AdjustmentKind.EARNING) {
            createAdjustmentEarningLine(targetRun, adjustmentItem, stagedAmount);
            return;
        }

        createAdjustmentDeductionLine(adjustmentItem, stagedAmount);
    }

    private BigDecimal calculateNetAdjustment(CorrectionSource source, BigDecimal correctedAmount) {
        return source.sourceLineType() == AdjustmentKind.EARNING
                ? correctedAmount.subtract(source.sourceAmount())
                : source.sourceAmount().subtract(correctedAmount);
    }

    private EarningsLine createAdjustmentEarningLine(
            PayrollRun targetRun,
            PayrollItem adjustmentItem,
            BigDecimal stagedAmount
    ) {
        EarningsLine line = new EarningsLine();
        line.setPayrollItem(adjustmentItem);
        line.setHours(BigDecimal.ONE.setScale(2));
        line.setRate(stagedAmount);
        line.setAmount(stagedAmount);
        line.setEarningsDate(targetRun.getPeriodEnd());
        line.setOvertime(false);
        line.setOvertimeRequest(null);
        line.setEarningsType(EarningsType.FIXED);
        line.setTaxableAmount(stagedAmount);
        line.setNonTaxableAmount(BigDecimal.ZERO.setScale(2));
        return earningsRepository.save(line);
    }

    private DeductionsLine createAdjustmentDeductionLine(
            PayrollItem adjustmentItem,
            BigDecimal stagedAmount
    ) {
        DeductionsLine line = new DeductionsLine();
        line.setPayrollItem(adjustmentItem);
        line.setDeductionType(DeductionType.MANUAL_ADJUSTMENT);
        line.setAmount(stagedAmount);
        return deductionsRepository.save(line);
    }


    private CorrectionSource resolveCorrectionSource(PayrollLineCorrectionDto payload) {
        validateCorrectionPayload(payload);
        PayrollItem sourceItem = getPostedSourceItem(payload.getSourceItemId());
        Employee employee = getCorrectionEmployee(sourceItem);
        return payload.getSourceLineType() == AdjustmentKind.EARNING
                ? resolveEarningCorrectionSource(sourceItem, employee, payload.getSourceLineId())
                : resolveDeductionCorrectionSource(sourceItem, employee, payload.getSourceLineId());
    }

    private void validateCorrectionPayload(PayrollLineCorrectionDto payload) {
        if (payload.getSourceItemId() == null) {
            throw new InvalidActionException("Source payslip item is required.");
        }
        if (payload.getSourceLineType() == null || payload.getSourceLineId() == null) {
            throw new InvalidActionException("Choose the exact payslip line to correct.");
        }
    }

    private PayrollItem getPostedSourceItem(Long sourceItemId) {
        return payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
                        sourceItemId,
                        PayrollStatus.POSTED,
                        RunType.ADJUSTMENT
                )
                .orElseThrow(() -> new ItemNotFoundException(sourceItemId));
    }

    private Employee getCorrectionEmployee(PayrollItem sourceItem) {
        Employee employee = sourceItem.getEmployee();
        if (employee == null || employee.getEmployeeId() == null) {
            throw new InvalidActionException("The source payslip is missing its employee reference.");
        }
        return employee;
    }

    private CorrectionSource resolveEarningCorrectionSource(PayrollItem sourceItem, Employee employee, Long sourceLineId) {
        EarningsLine line = earningsRepository.findById(sourceLineId)
                .orElseThrow(() -> new InvalidActionException("The selected earning line no longer exists."));
        validateSourceLineOwnership(sourceItem, line.getPayrollItem(), "earning");
        String label = earningsService.getOriginalEarnings(sourceItem.getItemId()).stream()
                .filter(entry -> entry.getEarningsId() != null && entry.getEarningsId().equals(sourceLineId))
                .map(EarningsDto::getDescription)
                .findFirst()
                .filter(value -> value != null && !value.isBlank())
                .orElse("Earning line");
        return new CorrectionSource(
                sourceItem,
                employee,
                AdjustmentKind.EARNING,
                sourceLineId,
                label,
                CommonCalculator.normalize(line.getAmount())
        );
    }

    private CorrectionSource resolveDeductionCorrectionSource(PayrollItem sourceItem, Employee employee, Long sourceLineId) {
        DeductionsLine line = deductionsRepository.findById(sourceLineId)
                .orElseThrow(() -> new InvalidActionException("The selected deduction line no longer exists."));
        validateSourceLineOwnership(sourceItem, line.getPayrollItem(), "deduction");
        String label = deductionsService.getOriginalDeductions(sourceItem.getItemId()).stream()
                .filter(entry -> entry.getDeductionsId() != null && entry.getDeductionsId().equals(sourceLineId))
                .map(DeductionsDto::getDescription)
                .findFirst()
                .filter(value -> value != null && !value.isBlank())
                .orElse("Deduction line");
        return new CorrectionSource(
                sourceItem,
                employee,
                AdjustmentKind.DEDUCTION,
                sourceLineId,
                label,
                CommonCalculator.normalize(line.getAmount())
        );
    }

    private void validateSourceLineOwnership(PayrollItem sourceItem, PayrollItem lineItem, String lineType) {
        if (lineItem == null || lineItem.getItemId() != sourceItem.getItemId()) {
            throw new InvalidActionException("That " + lineType + " line does not belong to the selected payslip.");
        }
    }

    private CorrectionSourceItemDto toCorrectionSourceItemDto(PayrollItem item) {
        CorrectionSourceItemDto dto = new CorrectionSourceItemDto();
        dto.setItemId(item.getItemId());
        if (item.getEmployee() != null) {
            dto.setEmpId(item.getEmployee().getEmployeeId());
            dto.setFirstName(item.getEmployee().getFirstName());
            dto.setLastName(item.getEmployee().getLastName());
        }
        dto.setGrossPay(item.getGrossPay());
        dto.setNetPay(item.getNetPay());
        dto.setRunDetails(payrollMapper.toRunDetailsDto(item.getPayrollRun()));
        return dto;
    }

    private void logLineCorrectionStaged(PayrollRun targetRun, CorrectionSource source, BigDecimal correctedAmount) {
        String username = getCurrentUsername();
        String label = abbreviate(source.label(), 60);
        String message = String.format(
                "Payslip correction staged by %s: emp #%d item #%d %s line #%d %s->%s in adj run #%d",
                username,
                source.employee().getEmployeeId(),
                source.sourceItem().getItemId(),
                label,
                source.sourceLineId(),
                source.sourceAmount().toPlainString(),
                correctedAmount.toPlainString(),
                targetRun.getPayId()
        );
        activityLogRepository.save(buildActivityLog(
                username,
                Activity.MODIFY,
                abbreviate(message, 255)
        ));
    }

    private ActivityLog buildActivityLog(String username, Activity activityType, String message) {
        ActivityLog activityLog = new ActivityLog();
        activityLog.setUsername(username);
        activityLog.setActivityType(activityType);
        activityLog.setMessage(message);
        activityLog.setTimestamp(java.time.Instant.now());
        return activityLog;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "unknownUser";
        }
        return authentication.getName();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, Math.max(0, maxLength));
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private ItemViewDto toDisplayedItemView(ItemViewDto itemView) {
        if (itemView.getItemId() == null) {
            return itemView;
        }

        BigDecimal grossPay = sumEarnings(itemView.getItemId());
        BigDecimal totalDeductions = sumDeductions(itemView.getItemId());
        itemView.setGrossPay(CommonCalculator.normalize(grossPay));
        itemView.setNetPay(CommonCalculator.normalizeNonNegative(grossPay.subtract(totalDeductions)));
        return withEmployerContributionTotal(itemView);
    }

    private ItemDetailsDto toDisplayedItemDetails(PayrollItem item) {
        ItemDetailsDto dto = payrollMapper.toItemDetailsDto(item);
        BigDecimal grossPay = sumEarnings(item.getItemId());
        BigDecimal totalDeductions = sumDeductions(item.getItemId());
        dto.setGrossPay(CommonCalculator.normalize(grossPay));
        dto.setNetPay(CommonCalculator.normalizeNonNegative(grossPay.subtract(totalDeductions)));
        return dto;
    }

    private BigDecimal sumEarnings(Long itemId) {
        return earningsService.getEarnings(itemId).stream()
                .map(EarningsDto::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private BigDecimal sumDeductions(Long itemId) {
        return deductionsService.getDeductions(itemId).stream()
                .map(DeductionsDto::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private BigDecimal sumEmployerContributions(Long itemId) {
        return employerContributionService.getEmployerContributions(itemId).stream()
                .map(EmployerContributionDto::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private ItemViewDto withEmployerContributionTotal(ItemViewDto itemView) {
        if (itemView == null || itemView.getItemId() == null) {
            return itemView;
        }

        itemView.setEmployerContributionTotal(CommonCalculator.normalize(sumEmployerContributions(itemView.getItemId())));
        return itemView;
    }

    private void assertRunIsEditable(PayrollRun run) {
        if (run.getPayrollStatus() == PayrollStatus.POSTED) {
            throw new InvalidActionException("This payroll run has already been posted.");
        }
    }

    private record CorrectionSource(
            PayrollItem sourceItem,
            Employee employee,
            AdjustmentKind sourceLineType,
            Long sourceLineId,
            String label,
            BigDecimal sourceAmount
    ) {}
}

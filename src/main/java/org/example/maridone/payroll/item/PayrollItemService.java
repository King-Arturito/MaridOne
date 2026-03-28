package org.example.maridone.payroll.item;

import jakarta.validation.Valid;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonCalculator;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.Activity;
import org.example.maridone.enums.AdjustmentKind;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.exception.notfound.RunNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.log.ActivityLogRepository;
import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.payroll.dto.CorrectionSourceItemDto;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.dto.EarningsDto;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PayrollItemService {
    private static final String CORRECTION_STAGE_PREFIX = "PAYCORR|";

    private final PayrollItemRepository payrollItemRepository;
    private final PayrollMapper payrollMapper;
    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRepository employeeRepository;
    private final DeductionsRepository deductionsRepository;
    private final EarningsRepository earningsRepository;
    private final EarningsService earningsService;
    private final DeductionsService deductionsService;
    private final ActivityLogRepository activityLogRepository;

    public PayrollItemService(
            PayrollItemRepository payrollItemRepository,
            PayrollMapper payrollMapper,
            PayrollRunRepository payrollRunRepository,
            EmployeeRepository employeeRepository,
            DeductionsRepository deductionsRepository,
            EarningsRepository earningsRepository,
            EarningsService earningsService,
            DeductionsService deductionsService,
            ActivityLogRepository activityLogRepository
    ) {
        this.payrollItemRepository = payrollItemRepository;
        this.payrollMapper = payrollMapper;
        this.payrollRunRepository = payrollRunRepository;
        this.employeeRepository = employeeRepository;
        this.deductionsRepository = deductionsRepository;
        this.earningsRepository = earningsRepository;
        this.earningsService = earningsService;
        this.deductionsService = deductionsService;
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional
    @ExecutionTime
    public List<ItemDetailsDto> getItems(Long empId) {
        List<PayrollItem> items = payrollItemRepository
                .findByEmployee_EmployeeIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNotOrderByPayrollRun_PeriodEndDescItemIdDesc(
                        empId,
                        PayrollStatus.POSTED,
                        RunType.ADJUSTMENT
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

    @Transactional
    @ExecutionTime
    public PayrollItemDto updateItem(PayrollItemDto payload, Long itemId) {
        PayrollItem item = payrollItemRepository.findById(itemId).orElseThrow(
                () -> new ItemNotFoundException(itemId)
        );

        item.setGrossPay(payload.getGrossPay());
        item.setNetPay(payload.getNetPay());
        payrollItemRepository.save(item);
        return payrollMapper.toPayrollItemDto(item);
    }

    @ExecutionTime
    public Page<ItemViewDto> getRunItems(Long payId, ItemFilter filter, Pageable pageable) {
        PayrollRun run = payrollRunRepository.findById(payId).orElseThrow(
                () -> new RunNotFoundException("Payroll Run ID:" + payId + " not found.")
        );
        Page<ItemViewDto> items = payrollItemRepository.findSpecificDetails(payId, filter, pageable);
        if (run.getPayrollStatus() != PayrollStatus.POSTED || run.getRunType() == RunType.ADJUSTMENT) {
            return items;
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
        Optional<PendingCorrection> existingCorrection = findPendingCorrection(
                payId,
                source.sourceItem().getItemId(),
                source.sourceLineType(),
                source.sourceLineId()
        );
        StagedAdjustment stagedAdjustment = stageLineCorrection(
                targetRun,
                adjustmentItem,
                source,
                correctedAmount,
                existingCorrection.orElse(null)
        );

        refreshItemTotals(adjustmentItem);
        payrollItemRepository.save(adjustmentItem);
        saveCorrectionStage(targetRun, source, correctedAmount, stagedAdjustment);
        logLineCorrectionStaged(targetRun, source, correctedAmount);
        return payrollMapper.toItemDetailsDto(adjustmentItem);
    }

    @Transactional
    public void applyStagedCorrections(PayrollRun targetRun) {
        if (targetRun == null || targetRun.getPayId() == null || targetRun.getRunType() != RunType.ADJUSTMENT) {
            return;
        }

        Map<String, PendingCorrection> pendingCorrections = getPendingCorrections(targetRun.getPayId());
        if (pendingCorrections.isEmpty()) {
            return;
        }

        Map<Long, PayrollItem> sourceItemsToRefresh = new LinkedHashMap<>();
        for (PendingCorrection correction : pendingCorrections.values()) {
            PayrollItem sourceItem = getPostedSourceItem(correction.sourceItemId());
            Employee employee = getCorrectionEmployee(sourceItem);
            CorrectionSource source = correction.sourceLineType() == AdjustmentKind.EARNING
                    ? resolveEarningCorrectionSource(sourceItem, employee, correction.sourceLineId())
                    : resolveDeductionCorrectionSource(sourceItem, employee, correction.sourceLineId());

            applyLineCorrection(source, correction.correctedAmount());
            sourceItemsToRefresh.put(sourceItem.getItemId(), sourceItem);
            logLineCorrectionApplied(targetRun, source, correction.correctedAmount());
        }

        sourceItemsToRefresh.values().forEach(item -> {
            refreshItemTotals(item);
            payrollItemRepository.save(item);
        });
    }

    public List<PayTypeSummaryDto> getSummarizedRunItems(Long payId) {
        List<PayTypeSummaryDto> summaryDtos = new ArrayList<>();

        deductionsRepository.getSummarizedDeductions(payId).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(p.getType(), p.getTotalAmount()))
        );

        earningsRepository.getSummarizedEarnings(payId).forEach(p ->
                summaryDtos.add(new PayTypeSummaryDto(p.getType(), p.getTotalAmount()))
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
        if (run.getPayrollStatus() == PayrollStatus.POSTED) {
            throw new InvalidActionException("This adjustment payroll run has already been posted.");
        }
        return run;
    }

    private BigDecimal normalizeCorrectedAmount(BigDecimal correctedAmount) {
        BigDecimal normalizedAmount = CommonCalculator.normalize(correctedAmount);
        if (normalizedAmount.compareTo(BigDecimal.ZERO.setScale(2)) < 0) {
            throw new InvalidActionException("Corrected amount cannot be negative.");
        }
        return normalizedAmount;
    }

    private void applyLineCorrection(CorrectionSource source, BigDecimal correctedAmount) {
        if (source.earningLine() != null) {
            source.earningLine().setAmount(correctedAmount);
            earningsRepository.save(source.earningLine());
            return;
        }
        if (source.deductionLine() != null) {
            source.deductionLine().setAmount(correctedAmount);
            deductionsRepository.save(source.deductionLine());
            return;
        }

        throw new InvalidActionException("The selected payslip line can no longer be corrected.");
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

    private StagedAdjustment stageLineCorrection(
            PayrollRun targetRun,
            PayrollItem adjustmentItem,
            CorrectionSource source,
            BigDecimal correctedAmount,
            PendingCorrection existingCorrection
    ) {
        BigDecimal netAdjustment = calculateNetAdjustment(source, correctedAmount);
        BigDecimal stagedAmount = CommonCalculator.normalize(netAdjustment);

        if (stagedAmount.compareTo(BigDecimal.ZERO.setScale(2)) == 0) {
            throw new InvalidActionException("The corrected amount matches the original line, so no adjustment is needed.");
        }

        deleteExistingDeductionLine(existingCorrection);
        EarningsLine line = upsertAdjustmentEarningLine(targetRun, adjustmentItem, existingCorrection, stagedAmount);
        return new StagedAdjustment(adjustmentItem.getItemId(), AdjustmentKind.EARNING, line.getEarningsId(), stagedAmount);
    }

    private BigDecimal calculateNetAdjustment(CorrectionSource source, BigDecimal correctedAmount) {
        return source.sourceLineType() == AdjustmentKind.EARNING
                ? correctedAmount.subtract(source.sourceAmount())
                : source.sourceAmount().subtract(correctedAmount);
    }

    private void deleteExistingDeductionLine(PendingCorrection existingCorrection) {
        if (existingCorrection == null || existingCorrection.adjustmentLineType() != AdjustmentKind.DEDUCTION) {
            return;
        }
        deductionsRepository.findById(existingCorrection.adjustmentLineId()).ifPresent(deductionsRepository::delete);
    }

    private EarningsLine upsertAdjustmentEarningLine(
            PayrollRun targetRun,
            PayrollItem adjustmentItem,
            PendingCorrection existingCorrection,
            BigDecimal stagedAmount
    ) {
        EarningsLine line = existingCorrection != null && existingCorrection.adjustmentLineType() == AdjustmentKind.EARNING
                ? earningsRepository.findById(existingCorrection.adjustmentLineId()).orElseGet(EarningsLine::new)
                : new EarningsLine();
        line.setPayrollItem(adjustmentItem);
        line.setHours(BigDecimal.ONE.setScale(2));
        line.setRate(stagedAmount);
        line.setAmount(stagedAmount);
        line.setEarningsDate(targetRun.getPeriodEnd());
        line.setOvertime(false);
        line.setOvertimeRequest(null);
        return earningsRepository.save(line);
    }

    private Optional<PendingCorrection> findPendingCorrection(
            Long payId,
            Long sourceItemId,
            AdjustmentKind sourceLineType,
            Long sourceLineId
    ) {
        String sourceKey = sourceKey(sourceItemId, sourceLineType, sourceLineId);
        return Optional.ofNullable(getPendingCorrections(payId).get(sourceKey));
    }

    private Map<String, PendingCorrection> getPendingCorrections(Long payId) {
        String prefix = CORRECTION_STAGE_PREFIX + "run=" + payId + "|";
        Map<String, PendingCorrection> pendingCorrections = new LinkedHashMap<>();
        activityLogRepository.findByActivityTypeAndMessageStartingWithOrderByActivityIdAsc(Activity.MODIFY, prefix)
                .stream()
                .map(ActivityLog::getMessage)
                .map(this::parsePendingCorrection)
                .flatMap(Optional::stream)
                .forEach(correction -> pendingCorrections.put(correction.sourceKey(), correction));
        return pendingCorrections;
    }

    private Optional<PendingCorrection> parsePendingCorrection(String message) {
        if (message == null || !message.startsWith(CORRECTION_STAGE_PREFIX)) {
            return Optional.empty();
        }

        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String token : message.substring(CORRECTION_STAGE_PREFIX.length()).split("\\|")) {
                int separatorIndex = token.indexOf('=');
                if (separatorIndex <= 0 || separatorIndex >= token.length() - 1) {
                    continue;
                }
                values.put(token.substring(0, separatorIndex), token.substring(separatorIndex + 1));
            }

            PendingCorrection correction = new PendingCorrection(
                    Long.parseLong(values.get("run")),
                    Long.parseLong(values.get("srcItem")),
                    AdjustmentKind.valueOf(values.get("srcType")),
                    Long.parseLong(values.get("srcLine")),
                    CommonCalculator.normalize(new BigDecimal(values.get("corr"))),
                    Long.parseLong(values.get("adjItem")),
                    AdjustmentKind.valueOf(values.get("adjType")),
                    Long.parseLong(values.get("adjLine"))
            );
            return Optional.of(correction);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private void saveCorrectionStage(
            PayrollRun targetRun,
            CorrectionSource source,
            BigDecimal correctedAmount,
            StagedAdjustment stagedAdjustment
    ) {
        String username = getCurrentUsername();
        String message = String.format(
                "%srun=%d|srcItem=%d|srcType=%s|srcLine=%d|corr=%s|adjItem=%d|adjType=%s|adjLine=%d",
                CORRECTION_STAGE_PREFIX,
                targetRun.getPayId(),
                source.sourceItem().getItemId(),
                source.sourceLineType().name(),
                source.sourceLineId(),
                correctedAmount.toPlainString(),
                stagedAdjustment.adjustmentItemId(),
                stagedAdjustment.adjustmentLineType().name(),
                stagedAdjustment.adjustmentLineId()
        );
        activityLogRepository.save(buildActivityLog(username, Activity.MODIFY, message));
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
                CommonCalculator.normalize(line.getAmount()),
                line,
                null
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
                CommonCalculator.normalize(line.getAmount()),
                null,
                line
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

    private void logLineCorrectionApplied(PayrollRun targetRun, CorrectionSource source, BigDecimal correctedAmount) {
        String username = getCurrentUsername();
        String label = abbreviate(source.label(), 60);
        String message = String.format(
                "Payslip correction applied by %s: emp #%d item #%d %s line #%d %s->%s from adj run #%d",
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
        itemView.setNetPay(CommonCalculator.normalize(grossPay.subtract(totalDeductions)));
        return itemView;
    }

    private ItemDetailsDto toDisplayedItemDetails(PayrollItem item) {
        ItemDetailsDto dto = payrollMapper.toItemDetailsDto(item);
        BigDecimal grossPay = sumEarnings(item.getItemId());
        BigDecimal totalDeductions = sumDeductions(item.getItemId());
        dto.setGrossPay(CommonCalculator.normalize(grossPay));
        dto.setNetPay(CommonCalculator.normalize(grossPay.subtract(totalDeductions)));
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

    private void refreshItemTotals(PayrollItem item) {
        if (item.getItemId() == 0L) {
            return;
        }

        List<EarningsLine> earnings = earningsRepository.findByPayrollItem_ItemId(item.getItemId());
        List<DeductionsLine> deductions = deductionsRepository.findByPayrollItem_ItemId(item.getItemId());

        item.setEarnings(earnings);
        item.setDeductions(deductions);

        BigDecimal totalEarnings = earnings.stream()
                .map(EarningsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = deductions.stream()
                .map(DeductionsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        item.setGrossPay(CommonCalculator.normalize(totalEarnings));
        item.setNetPay(CommonCalculator.normalize(totalEarnings.subtract(totalDeductions)));
    }

    private String sourceKey(Long sourceItemId, AdjustmentKind sourceLineType, Long sourceLineId) {
        return sourceItemId + "|" + sourceLineType.name() + "|" + sourceLineId;
    }

    private record CorrectionSource(
            PayrollItem sourceItem,
            Employee employee,
            AdjustmentKind sourceLineType,
            Long sourceLineId,
            String label,
            BigDecimal sourceAmount,
            EarningsLine earningLine,
            DeductionsLine deductionLine
    ) {}

    private record StagedAdjustment(
            Long adjustmentItemId,
            AdjustmentKind adjustmentLineType,
            Long adjustmentLineId,
            BigDecimal stagedAmount
    ) {}

    private record PendingCorrection(
            Long payId,
            Long sourceItemId,
            AdjustmentKind sourceLineType,
            Long sourceLineId,
            BigDecimal correctedAmount,
            Long adjustmentItemId,
            AdjustmentKind adjustmentLineType,
            Long adjustmentLineId
    ) {
        private String sourceKey() {
            return sourceItemId + "|" + sourceLineType.name() + "|" + sourceLineId;
        }
    }
}

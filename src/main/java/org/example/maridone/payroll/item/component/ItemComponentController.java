package org.example.maridone.payroll.item.component;

import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.dto.EarningsDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payroll/item")
public class ItemComponentController {
    private final EarningsService earningsService;
    private final DeductionsService deductionsService;

    public ItemComponentController(EarningsService earningsService, DeductionsService deductionsService) {
        this.earningsService = earningsService;
        this.deductionsService = deductionsService;
    }

    @GetMapping("/earnings/{itemId}")
    @PreAuthorize("@itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public List<EarningsDto> getEarnings(@PathVariable Long itemId) {
        return earningsService.getEarnings(itemId);
    }

    @GetMapping("/deductions/{itemId}")
    @PreAuthorize("@itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public List<DeductionsDto> getDeductions(@PathVariable Long itemId) {
        return deductionsService.getDeductions(itemId);
    }

    //create earnings
    //create deductions
}

package org.example.maridone.enums;

/**
 * DeductionType defines the different categories of salary deductions
 * based on Philippine Labor Laws and 2026 BIR Tax Tables.
 */
public enum DeductionType {
    // --- Statutory Government Contributions ---
    /** Home Development Mutual Fund (HDMF) - Fixed at ₱200 for salaries >₱10k */   
    PAGIBIG,
    
    /** Social Security System - 15% total rate (5% EE Share) */
    SSS,
    
    /** Philippine Health Insurance Corporation - 5% total rate (2.5% EE Share) */
    PHILHEALTH,

    // --- Withholding Tax Brackets (TRAIN Law 2026) ---
    /** 0% Tax: Annual income ₱250,000 and below */
    BRACKET_LEVEL_ONE,
    
    /** 15% of excess over ₱250,000: Annual income ₱250k - ₱400k */
    BRACKET_LEVEL_TWO,
    
    /** ₱22,500 + 20% of excess over ₱400,000: Annual income ₱400k - ₱800k */
    BRACKET_LEVEL_THREE,
    
    /** ₱102,500 + 25% of excess over ₱800,000: Annual income ₱800k - ₱2M */
    BRACKET_LEVEL_FOUR,
    
    /** ₱402,500 + 30% of excess over ₱2M: Annual income ₱2M - ₱8M */
    BRACKET_LEVEL_FIVE,
    
    /** ₱2,202,500 + 35% of excess over ₱8M: Annual income > ₱8M */
    BRACKET_LEVEL_SIX,

    // --- Loans & Advances ---
    /** Repayment for SSS Salary or Calamity Loans */
    SSS_LOAN,
    
    /** Repayment for Pag-IBIG Short-term or Housing Loans */
    PAGIBIG_LOAN,
    
    /** Company-initiated cash advances or "Vale" */
    CASH_ADVANCE,

    // --- Attendance & Performance ---
    /** Deductions due to late arrivals or tardiness */
    LATE_PENALTY,
    
    /** Deductions for Unpaid Leaves or absences without pay */
    ABSENT_DEDUCTION,

    // --- Others ---
    /** Deductions for damage or loss of company-issued equipment (as per Handbook) */
    EQUIPMENT_LOSS_PENALTY,
    
    /** Premium for voluntary Health Maintenance Organization upgrades */
    HMO_UPGRADE
}

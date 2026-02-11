package org.example.maridone.payroll;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.itemcomponent.DeductionsLine;
import org.example.maridone.payroll.itemcomponent.EarningsLine;
import org.example.maridone.payroll.run.PayrollRun;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "payroll_item")
public class PayrollItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @ManyToOne
    @JoinColumn(name = "emp_id", nullable = false)
    @JsonIgnore
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "pay_id", nullable = false)
    @JsonIgnore
    private PayrollRun payrollRun;

    //calculate by earnings
    @Column(name = "gross_pay", nullable = false)
    private BigDecimal grossPay;

    //calculate by earnings - deductions
    @Column(name = "net_pay", nullable = false)
    private BigDecimal netPay;

    @OneToMany(mappedBy = "payrollItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DeductionsLine> deductions;

    @OneToMany(mappedBy = "payrollItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EarningsLine> earnings;

    @OneToMany(mappedBy = "payrollItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DisputeRequest> disputes;

    public long getItemId() {
        return itemId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public BigDecimal getGrossPay() {
        return grossPay;
    }

    public void setGrossPay(BigDecimal grossPay) {
        this.grossPay = grossPay;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }

    public List<DeductionsLine> getDeductions() {
        return deductions;
    }

    public void setDeductions(List<DeductionsLine> deductions) {
        this.deductions = deductions;
    }

    public List<EarningsLine> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<EarningsLine> earnings) {
        this.earnings = earnings;
    }

    public List<DisputeRequest> getDisputes() {
        return disputes;
    }

    public void setDisputes(List<DisputeRequest> disputes) {
        this.disputes = disputes;
    }
}

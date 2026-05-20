CREATE INDEX IF NOT EXISTS idx_attendance_log_emp_timestamp
    ON attendance_log (emp_id, timestamp);

CREATE INDEX IF NOT EXISTS idx_template_shift_schedule_emp_day
    ON template_shift_schedule (emp_id, day_of_week);

CREATE INDEX IF NOT EXISTS idx_daily_shift_schedule_shift_start
    ON daily_shift_schedule (shift_id, start_date_time);

CREATE INDEX IF NOT EXISTS idx_leave_request_emp_status_period
    ON leave_request (emp_id, request_status, start_date_time, end_date_time);

CREATE INDEX IF NOT EXISTS idx_overtime_request_emp_status_paid_date
    ON overtime_request (emp_id, request_status, is_paid, work_date);

CREATE INDEX IF NOT EXISTS idx_payroll_item_emp_pay
    ON payroll_item (emp_id, pay_id);

CREATE INDEX IF NOT EXISTS idx_payroll_item_pay_emp
    ON payroll_item (pay_id, emp_id);

CREATE INDEX IF NOT EXISTS idx_payroll_run_period_type
    ON payroll_run (period_start, period_end, run_type);

CREATE INDEX IF NOT EXISTS idx_payroll_run_payment_status
    ON payroll_run (payment_date, payroll_status);

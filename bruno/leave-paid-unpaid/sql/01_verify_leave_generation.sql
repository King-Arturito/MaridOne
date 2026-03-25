SELECT
    e.emp_id,
    u.username,
    e.position,
    e.first_name,
    e.last_name
FROM employee e
JOIN user_account u ON u.emp_id = e.emp_id
WHERE u.username IN ('userName2', 'userName10')
ORDER BY e.emp_id;

SELECT
    emp_id,
    day_of_week,
    start_time,
    end_time
FROM template_shift_schedule
WHERE emp_id = 10
ORDER BY
    CASE day_of_week
        WHEN 'MONDAY' THEN 1
        WHEN 'TUESDAY' THEN 2
        WHEN 'WEDNESDAY' THEN 3
        WHEN 'THURSDAY' THEN 4
        WHEN 'FRIDAY' THEN 5
        WHEN 'SATURDAY' THEN 6
        WHEN 'SUNDAY' THEN 7
        ELSE 99
    END;

SELECT
    leave_id,
    emp_id,
    leave_type,
    balance_hours
FROM leave_balance
WHERE emp_id = 10
  AND leave_type = 'SICK_LEAVE';

SELECT
    request_id,
    emp_id,
    request_status,
    is_paid,
    start_date_time,
    end_date_time,
    reason
FROM leave_request
WHERE emp_id = 10
  AND reason LIKE 'BRUNO Leave Generation%'
ORDER BY request_id;

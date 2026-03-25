BEGIN;

DELETE FROM leave_request
WHERE emp_id = 10
  AND reason LIKE 'BRUNO Leave Generation%';

DELETE FROM leave_balance
WHERE emp_id = 10
  AND leave_type = 'SICK_LEAVE';

INSERT INTO leave_balance (balance_hours, leave_type, emp_id)
VALUES (4.00, 'SICK_LEAVE', 10);

COMMIT;

# Leave Paid / Unpaid Generation Test Guide

This Bruno collection validates the current `LeaveService#createLeaveRequest(...)` behavior.

It covers two generation branches:

- fully paid leave when requested hours are within the available balance
- split paid and unpaid leave when requested hours exceed the available balance

## Important behavior note

The current endpoint returns generated `LeaveRequest` objects but does not persist them to `leave_request`, and it does not deduct from `leave_balance`.

That means a successful run should show:

- correct paid and unpaid segmentation in the API response
- unchanged leave balance in the database
- no newly inserted `leave_request` rows for the Bruno test reasons

## Test actors

These are seeded by the dev profile:

- HR user: `userName2` / `test`
- Employee user: `userName10` / `test`
- Employee under test: `emp_id = 10`
- Leave type under test: `SICK_LEAVE`

## Recommended setup

1. Start the backend with the `dev` profile.
2. Run `sql/00_reset_employee10_sick_leave.sql`.
3. Open the Bruno collection and select `environments/local.bru`.
4. Log in as HR and employee, then paste the access tokens into the environment.

## State-by-state flow

### State 0: Reset the test row

Run `sql/00_reset_employee10_sick_leave.sql`.

Expected database state:

- employee `10` has `SICK_LEAVE = 4.00`
- previous Bruno test requests are removed

### State 1: Login as HR

Run `auth/00 Login HR.bru`.

Expected response:

- HTTP `200`
- body contains `accessToken` and `refreshToken`

### State 2: Login as employee 10

Run `auth/01 Login Employee 10.bru`.

Expected response:

- HTTP `200`
- body contains `accessToken` and `refreshToken`

### State 3: Verify the test balance

Run `leave/11 Get My Test Leave Balance.bru`.

Expected response:

- HTTP `200`
- one returned balance row for `SICK_LEAVE` with `balanceHours = 4.00`

### State 4: Fully paid leave generation

Run `leave/20 Generate Fully Paid Leave (3h against 4h balance).bru`.

Expected response:

- HTTP `200`
- array length is `1`
- `requestStatus = "PENDING"`
- `paid = true`
- returned start and end match `2026-05-04T09:00:00` to `2026-05-04T12:00:00`

Example:

```json
[
  {
    "requestId": null,
    "startDateTime": "2026-05-04T09:00:00",
    "endDateTime": "2026-05-04T12:00:00",
    "approver": null,
    "requestStatus": "PENDING",
    "reason": "BRUNO Leave Generation - Fully Paid - 3h against 4h balance",
    "approverReason": null,
    "paid": true
  }
]
```

### State 5: Split paid and unpaid leave generation

Run `leave/21 Generate Split Paid + Unpaid Leave (9h against 4h balance).bru`.

Expected response:

- HTTP `200`
- array length is `2`
- item 1 is the paid segment
- item 2 is the unpaid segment

Expected split:

- paid: `2026-05-05T08:00:00` to `2026-05-05T12:00:00`
- unpaid: `2026-05-05T12:00:00` to `2026-05-05T17:00:00`

Example:

```json
[
  {
    "requestId": null,
    "startDateTime": "2026-05-05T08:00:00",
    "endDateTime": "2026-05-05T12:00:00",
    "approver": null,
    "requestStatus": "PENDING",
    "reason": "BRUNO Leave Generation - Split Paid Unpaid - 9h against 4h balance",
    "approverReason": null,
    "paid": true
  },
  {
    "requestId": null,
    "startDateTime": "2026-05-05T12:00:00",
    "endDateTime": "2026-05-05T17:00:00",
    "approver": null,
    "requestStatus": "PENDING",
    "reason": "BRUNO Leave Generation - Split Paid Unpaid - 9h against 4h balance",
    "approverReason": null,
    "paid": false
  }
]
```

## Database verification

Run `sql/01_verify_leave_generation.sql`.

Expected result with the current implementation:

- `leave_balance` for `emp_id = 10` and `SICK_LEAVE` remains `4.00`
- no new `leave_request` row exists for the Bruno reasons

That is expected because generation currently returns transient request objects only.

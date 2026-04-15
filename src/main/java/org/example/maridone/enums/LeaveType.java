package org.example.maridone.enums;


public enum LeaveType {
    //resets every year
    VACATION_LEAVE,
    //5 working days of sick leave with pay per year
    SICK_LEAVE,
    /*
        Under the Expanded Maternity Leave Law, the compensable number of days for maternity benefit
        leave are the following:
            - 105 days for live childbirth, regardless of the mode of delivery (whether CS or normal)
            - 60 days paid leave for miscarriage and emergency termination of pregnancy (ETP)
            - Additional 15 days paid leave if the female employee is a qualified solo parent
            - With an option to extend for an additional thirty (30) days without pay in case of live childbirth.
            - Employees are required to notify the Human Resources Department within sixty (60) days upon
            confirmation of pregnancy and must submit all the necessary documents for the Maternity Leave filing.
    */
    MATERNITY_LEAVE,
    PATERNITY_LEAVE,
    SOLO_PARENT_LEAVE,
    MAGNA_CARTA,
    RA9262,
    EMERGENCY_LEAVE
}

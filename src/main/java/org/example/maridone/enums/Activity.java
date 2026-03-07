package org.example.maridone.enums;

//For ActivityLog
public enum Activity {
    LOG,
    //read from database
    READ,
    //change values from database
    MODIFY,
    //add rows to database
    CREATE,
    //remove rows from database
    DELETE,
    //unknown, usually when logging in or error
    UNKNOWN,
    //used for @Scheduled jobs
    SYSTEM
}
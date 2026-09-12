package com.manuscriptvalidation.validation_service.enums;

public enum ActivityType {
    REQUEST_CREATED,
    FILE_UPLOAD_STARTED,
    FILE_UPLOADED,
    
    VALIDATION_STARTED,
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    
    FILE_ARCHIVED_PASSED,
    FILE_ARCHIVED_FAILED,
    
    EMAIL_SENT,
    EMAIL_NOT_SENT
}
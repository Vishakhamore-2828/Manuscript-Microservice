package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;

import org.springframework.stereotype.Component;

/**
 * Validates file name to ensure it's valid and not empty
 * Works with file name string extracted from S3 reference
 */
@Component
public class FileNameValidator {

    /**
     * Validate file name string
     * @param fileName File name extracted from S3 reference or manifest
     * @return ValidationResult
     */
    public ValidationResultDto validate(String fileName) {
        ValidationResultDto result = new ValidationResultDto();

        if (fileName == null || fileName.isBlank()) {
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_FILE_NAME",
                            "Manuscript file name is missing"
                    )
            );
            result.setPassed(false);
            return result;
        }

        // Validate file name doesn't contain invalid characters
        if (!isValidFileName(fileName)) {
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_FILE_NAME",
                            "File name contains invalid characters: " + fileName
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Check if file name is valid
     * Should not contain path separators or other invalid characters
     */
    private boolean isValidFileName(String fileName) {
        // File name should not be empty
        if (fileName.isBlank()) {
            return false;
        }

        // File name should not contain path separators
        if (fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }

        // File name should not contain null bytes or other control characters
        if (fileName.contains("\u0000")) {
            return false;
        }

        // File name should not be just dots
        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        }

        return true;
    }
}
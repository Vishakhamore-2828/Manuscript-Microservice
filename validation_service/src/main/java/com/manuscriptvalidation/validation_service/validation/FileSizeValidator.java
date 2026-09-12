package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;

import org.springframework.stereotype.Component;

/**
 * Validates file size to ensure it doesn't exceed maximum limit
 * Works with byte array content (downloaded from S3) or file size in bytes
 */
@Component
public class FileSizeValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Validate file size based on byte array content
     * @param fileContent Downloaded file content
     * @return ValidationResult
     */
    public ValidationResultDto validateByteArray(byte[] fileContent) {
        ValidationResultDto result = new ValidationResultDto();

        if (fileContent == null || fileContent.length == 0) {
            result.addError(
                    new ValidationErrorDto(
                            "FILE_NOT_FOUND",
                            "Manuscript file is missing"
                    )
            );
            result.setPassed(false);
            return result;
        }

        if (fileContent.length > MAX_FILE_SIZE) {
            result.addError(
                    new ValidationErrorDto(
                            "FILE_SIZE_EXCEEDED",
                            "Manuscript file size (" + formatBytes(fileContent.length) + ") exceeds maximum allowed size of " + formatBytes(MAX_FILE_SIZE)
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Validate file size based on file size in bytes
     * @param fileSize File size in bytes
     * @return ValidationResult
     */
    public ValidationResultDto validateBySize(long fileSize) {
        ValidationResultDto result = new ValidationResultDto();

        if (fileSize <= 0) {
            result.addError(
                    new ValidationErrorDto(
                            "FILE_NOT_FOUND",
                            "Manuscript file is missing"
                    )
            );
            result.setPassed(false);
            return result;
        }

        if (fileSize > MAX_FILE_SIZE) {
            result.addError(
                    new ValidationErrorDto(
                            "FILE_SIZE_EXCEEDED",
                            "Manuscript file size (" + formatBytes(fileSize) + ") exceeds maximum allowed size of " + formatBytes(MAX_FILE_SIZE)
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Get maximum file size limit
     */
    public static long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    /**
     * Format bytes to human-readable format
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.LongPredicate;

/**
 * Validates file size to ensure it does not exceed the maximum allowed limit (10MB)
 */
@Component
public class FileSizeValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileSizeValidator.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private static final LongPredicate IS_POSITIVE_SIZE = size -> size > 0;
    private static final LongPredicate IS_WITHIN_LIMIT = size -> size <= MAX_FILE_SIZE;

    public ValidationResultDto validateByteArray(byte[] fileContent) {
        ValidationResultDto result = new ValidationResultDto();

        if (fileContent == null || fileContent.length == 0) {
            logger.warn("❌ File size validation failed: Content is missing or empty");
            result.addError(new ValidationErrorDto("FILE_NOT_FOUND", "Manuscript file is missing"));
            result.setPassed(false);
            return result;
        }

        return validateBySize(fileContent.length);
    }

    public ValidationResultDto validateBySize(long fileSize) {
        ValidationResultDto result = new ValidationResultDto();

        if (!IS_POSITIVE_SIZE.test(fileSize)) {
            logger.warn("❌ File size validation failed: Non-positive size {}", fileSize);
            result.addError(new ValidationErrorDto("FILE_NOT_FOUND", "Manuscript file is missing"));
            result.setPassed(false);
            return result;
        }

        if (!IS_WITHIN_LIMIT.test(fileSize)) {
            String errorMsg = "Manuscript file size (" + formatBytes(fileSize) + ") exceeds maximum allowed size of " + formatBytes(MAX_FILE_SIZE);
            logger.warn("❌ File size validation failed: {}", errorMsg);
            result.addError(new ValidationErrorDto("FILE_SIZE_EXCEEDED", errorMsg));
            result.setPassed(false);
            return result;
        }

        logger.debug("✅ File size validation passed ({} bytes)", fileSize);
        result.setPassed(true);
        return result;
    }

    public static long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
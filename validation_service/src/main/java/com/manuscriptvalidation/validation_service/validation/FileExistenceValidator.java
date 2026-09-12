package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;

import org.springframework.stereotype.Component;

/**
 * Validates file existence by checking if downloaded content is valid
 * Works with byte array content downloaded from S3
 */
@Component
public class FileExistenceValidator {

    /**
     * Validate file existence based on downloaded content
     * @param fileContent Downloaded file content from S3
     * @return ValidationResult
     */
    public ValidationResultDto validate(byte[] fileContent) {
        ValidationResultDto result = new ValidationResultDto();

        if (fileContent == null || fileContent.length == 0) {
            result.addError(
                    new ValidationErrorDto(
                            "FILE_NOT_FOUND",
                            "Manuscript file is missing or empty in S3"
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Validate that content is not null or empty
     * @param content File content bytes
     * @return true if file exists/has content, false otherwise
     */
    public boolean fileExists(byte[] content) {
        return content != null && content.length > 0;
    }
}
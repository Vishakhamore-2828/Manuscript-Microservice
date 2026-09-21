package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Validates file existence by verifying that downloaded S3 content contains non-empty byte data
 */
@Component
public class FileExistenceValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileExistenceValidator.class);

    private static final Predicate<byte[]> HAS_CONTENT = bytes -> bytes != null && bytes.length > 0;

    public ValidationResultDto validate(byte[] fileContent) {
        ValidationResultDto result = new ValidationResultDto();

        if (!HAS_CONTENT.test(fileContent)) {
            logger.warn("❌ File existence validation failed: content is null or empty");
            result.addError(new ValidationErrorDto("FILE_NOT_FOUND", "Manuscript file is missing or empty in S3"));
            result.setPassed(false);
            return result;
        }

        logger.debug("✅ File existence validation passed ({} bytes present)", fileContent.length);
        result.setPassed(true);
        return result;
    }

    public boolean fileExists(byte[] content) {
        return HAS_CONTENT.test(content);
    }
}
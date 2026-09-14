package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Validates file name to ensure it's valid, secure, and contains no directory traversal or control characters
 */
@Component
public class FileNameValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileNameValidator.class);

    private static final Predicate<String> IS_BLANK = s -> s == null || s.isBlank();
    private static final Predicate<String> HAS_NO_PATH_SEPARATORS = name -> !name.contains("/") && !name.contains("\\");
    private static final Predicate<String> HAS_NO_NULL_BYTES = name -> !name.contains("\u0000");
    private static final Predicate<String> IS_NOT_DOT_DIRECTORY = name -> !name.equals(".") && !name.equals("..");

    private static final Predicate<String> IS_VALID_FILENAME = HAS_NO_PATH_SEPARATORS
            .and(HAS_NO_NULL_BYTES)
            .and(IS_NOT_DOT_DIRECTORY);

    public ValidationResultDto validate(String fileName) {
        ValidationResultDto result = new ValidationResultDto();

        if (IS_BLANK.test(fileName)) {
            logger.warn("❌ File name validation failed: filename is missing or blank");
            result.addError(new ValidationErrorDto("INVALID_FILE_NAME", "Manuscript file name is missing"));
            result.setPassed(false);
            return result;
        }

        if (!IS_VALID_FILENAME.test(fileName)) {
            logger.warn("❌ File name validation failed: invalid format or path characters in '{}'", fileName);
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_FILE_NAME",
                            "File name contains invalid characters: " + fileName
                    )
            );
            result.setPassed(false);
            return result;
        }

        logger.debug("✅ File name validation passed for '{}'", fileName);
        result.setPassed(true);
        return result;
    }
}
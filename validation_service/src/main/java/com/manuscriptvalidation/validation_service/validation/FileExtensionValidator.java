package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Validates manuscript file extensions against allowed set (.pdf, .docx, .epub)
 */
@Component
public class FileExtensionValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileExtensionValidator.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf",
            ".docx",
            ".epub"
    );

    private static final Predicate<String> IS_BLANK = s -> s == null || s.isBlank();
    private static final Predicate<String> IS_ALLOWED_EXTENSION = ALLOWED_EXTENSIONS::contains;

    public ValidationResultDto validate(String s3Reference) {
        ValidationResultDto result = new ValidationResultDto();

        if (IS_BLANK.test(s3Reference)) {
            logger.warn("❌ File extension validation failed: S3 reference is missing");
            result.addError(new ValidationErrorDto("MISSING_S3_REFERENCE", "S3 reference is missing"));
            result.setPassed(false);
            return result;
        }

        String extension = getFileExtension(s3Reference);

        if (IS_BLANK.test(extension)) {
            logger.warn("❌ File extension validation failed: No extension found in S3 reference '{}'", s3Reference);
            result.addError(new ValidationErrorDto("NO_FILE_EXTENSION", "S3 object has no file extension"));
            result.setPassed(false);
            return result;
        }

        if (!IS_ALLOWED_EXTENSION.test(extension)) {
            logger.warn("❌ File extension validation failed: '{}' not supported", extension);
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_FILE_EXTENSION",
                            "File extension '" + extension + "' is not supported. Allowed: .pdf, .docx, .epub"
                    )
            );
            result.setPassed(false);
            return result;
        }

        logger.debug("✅ File extension validation passed for '{}'", extension);
        result.setPassed(true);
        return result;
    }

    public String getFileExtension(String s3Reference) {
        if (s3Reference == null) return "";
        int lastDotIndex = s3Reference.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : s3Reference.substring(lastDotIndex).toLowerCase();
    }

    public String getFileFormat(String s3Reference) {
        String extension = getFileExtension(s3Reference);
        return extension.startsWith(".") ? extension.substring(1) : extension;
    }
}
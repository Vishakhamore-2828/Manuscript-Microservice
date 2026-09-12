package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FileExtensionValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf",
            ".docx",
            ".epub"
    );

    /**
     * Validate file extension from S3 reference path
     * Example: s3://bucket/path/manuscript.pdf
     */
    public ValidationResultDto validate(String s3Reference) {

        ValidationResultDto result = new ValidationResultDto();

        if (s3Reference == null || s3Reference.isBlank()) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_S3_REFERENCE",
                            "S3 reference is missing"
                    )
            );
            result.setPassed(false);
            return result;
        }

        String extension = getFileExtension(s3Reference);

        if (extension.isBlank()) {
            result.addError(
                    new ValidationErrorDto(
                            "NO_FILE_EXTENSION",
                            "S3 object has no file extension"
                    )
            );
            result.setPassed(false);
            return result;
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_FILE_EXTENSION",
                            "File extension '" + extension + "' is not supported. Allowed: .pdf, .docx, .epub"
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Extract file extension from S3 path
     * s3://bucket/folder/file.pdf -> .pdf
     */
    public String getFileExtension(String s3Reference) {
        int lastDotIndex = s3Reference.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return s3Reference.substring(lastDotIndex).toLowerCase();
    }

    /**
     * Extract file format (without dot)
     * s3://bucket/folder/file.pdf -> pdf
     */
    public String getFileFormat(String s3Reference) {
        String extension = getFileExtension(s3Reference);
        return extension.startsWith(".") ? extension.substring(1) : extension;
    }
}
// filepath: c:\Users\vishakha.more\Projects\validation_service\src\main\java\com\manuscriptvalidation\validation_service\service\ValidationService.java
package com.manuscriptvalidation.validation_service.service;

import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.validation.RequiredMetadataValidator;
import com.manuscriptvalidation.validation_service.validation.FileExtensionValidator;
import com.manuscriptvalidation.validation_service.validation.FileSizeValidator;
import com.manuscriptvalidation.validation_service.validation.FileNameValidator;
import com.manuscriptvalidation.validation_service.validation.FileExistenceValidator;
import com.manuscriptvalidation.validation_service.validation.FileCorruptionValidator;
import com.manuscriptvalidation.validation_service.validation.IsbnValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ValidationService - Orchestrates individual validator components
 */
@Service
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    private final RequiredMetadataValidator requiredMetadataValidator;
    private final FileExtensionValidator fileExtensionValidator;
    private final FileSizeValidator fileSizeValidator;
    private final FileNameValidator fileNameValidator;
    private final FileExistenceValidator fileExistenceValidator;
    private final FileCorruptionValidator fileCorruptionValidator;
    private final IsbnValidator isbnValidator;

    public ValidationService(RequiredMetadataValidator requiredMetadataValidator,
                             FileExtensionValidator fileExtensionValidator,
                             FileSizeValidator fileSizeValidator,
                             FileNameValidator fileNameValidator,
                             FileExistenceValidator fileExistenceValidator,
                             FileCorruptionValidator fileCorruptionValidator,
                             IsbnValidator isbnValidator) {
        this.requiredMetadataValidator = requiredMetadataValidator;
        this.fileExtensionValidator = fileExtensionValidator;
        this.fileSizeValidator = fileSizeValidator;
        this.fileNameValidator = fileNameValidator;
        this.fileExistenceValidator = fileExistenceValidator;
        this.fileCorruptionValidator = fileCorruptionValidator;
        this.isbnValidator = isbnValidator;
    }

    /**
     * Validate metadata of the uploaded event
     */
    public ValidationResultDto validateMetadata(FileUploadedEvent event) {
        logger.info("🔍 Validating metadata for requestId: {}", event.getRequestId());
        ValidationResultDto result = requiredMetadataValidator.validate(event);
        
        if (result.isPassed()) {
            logger.info("✅ Metadata validation passed for requestId: {}", event.getRequestId());
        } else {
            logger.warn("❌ Metadata validation failed for requestId: {}", event.getRequestId());
        }
        return result;
    }

    /**
     * Validate ISBN
     */
    public ValidationResultDto validateISBN(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            ValidationResultDto result = new ValidationResultDto();
            result.setPassed(true);
            return result;
        }
        
        logger.info("🔍 Validating ISBN: {}", isbn);
        ValidationResultDto result = isbnValidator.validate(isbn);
        
        if (result.isPassed()) {
            logger.info("✅ ISBN validation passed");
        } else {
            logger.warn("❌ ISBN validation failed for: {}", isbn);
        }
        return result;
    }

    /**
     * Validate file extension from S3 reference
     */
    public ValidationResultDto validateFileExtension(String s3Reference) {
        logger.info("🔍 Validating file extension from S3: {}", s3Reference);
        ValidationResultDto result = fileExtensionValidator.validate(s3Reference);
        
        if (result.isPassed()) {
            logger.info("✅ File extension validation passed");
        } else {
            logger.warn("❌ File extension validation failed for: {}", s3Reference);
        }
        return result;
    }

    /**
     * Extract file format from S3 reference
     */
    public String extractFileFormat(String s3Reference) {
        return fileExtensionValidator.getFileFormat(s3Reference);
    }

    /**
     * Validate file name
     */
    public ValidationResultDto validateFileName(String fileName) {
        logger.info("🔍 Validating file name: {}", fileName);
        ValidationResultDto result = fileNameValidator.validate(fileName);
        
        if (result.isPassed()) {
            logger.info("✅ File name validation passed for: {}", fileName);
        } else {
            logger.warn("❌ File name validation failed for: {}", fileName);
        }
        return result;
    }

    /**
     * Validate file existence
     */
    public ValidationResultDto validateFileExistence(byte[] fileContent) {
        logger.info("🔍 Validating file existence in S3");
        ValidationResultDto result = fileExistenceValidator.validate(fileContent);
        
        if (result.isPassed()) {
            logger.info("✅ File existence validation passed");
        } else {
            logger.warn("❌ File existence validation failed");
        }
        return result;
    }

    /**
     * Validate file size based on byte array content
     */
    public ValidationResultDto validateFileSize(byte[] fileContent) {
        logger.info("🔍 Validating file size");
        ValidationResultDto result = fileSizeValidator.validateByteArray(fileContent);
        
        if (result.isPassed()) {
            logger.info("✅ File size validation passed");
        } else {
            logger.warn("❌ File size validation failed");
        }
        return result;
    }

    /**
     * Validate file integrity (check for corruption)
     */
    public ValidationResultDto validateFileCorruption(byte[] fileContent, String fileName) {
        logger.info("🔍 Validating file integrity for file: {}", fileName);
        ValidationResultDto result = fileCorruptionValidator.validate(fileContent, fileName);
        
        if (result.isPassed()) {
            logger.info("✅ File integrity validation passed for: {}", fileName);
        } else {
            logger.warn("❌ File integrity validation failed for: {}", fileName);
        }
        return result;
    }
}
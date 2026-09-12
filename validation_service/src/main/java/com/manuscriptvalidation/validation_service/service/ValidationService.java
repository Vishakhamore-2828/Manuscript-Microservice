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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    @Autowired
    private RequiredMetadataValidator requiredMetadataValidator;

    @Autowired
    private FileExtensionValidator fileExtensionValidator;

    @Autowired
    private FileSizeValidator fileSizeValidator;

    @Autowired
    private FileNameValidator fileNameValidator;

    @Autowired
    private FileExistenceValidator fileExistenceValidator;

    @Autowired
    private FileCorruptionValidator fileCorruptionValidator;

    @Autowired
    private IsbnValidator isbnValidator;

    /**
     * Validate metadata of the uploaded event
     */
    public ValidationResultDto validateMetadata(FileUploadedEvent event) {
        logger.info("🔍 Starting metadata validation for requestId: {}", event.getRequestId());
        
        ValidationResultDto result = requiredMetadataValidator.validate(event);
        
        if (result.isPassed()) {
            logger.info("✅ Metadata validation passed for requestId: {}", event.getRequestId());
        } else {
            logger.error("❌ Metadata validation failed for requestId: {}", event.getRequestId());
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
            logger.error("❌ File extension validation failed");
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
     * Handle validation success
     */
    public void handleValidationSuccess(FileUploadedEvent event) {
        logger.info("✅ Validation successful for requestId: {}", event.getRequestId());
    }

    /**
     * Handle validation failure
     */
    public void handleValidationFailure(FileUploadedEvent event, ValidationResultDto result) {
        logger.error("❌ Validation failed for requestId: {} with errors: {}", 
                event.getRequestId(), result.getErrors());
    }

    /**
     * Validate file name
     */
    public ValidationResultDto validateFileName(String fileName) {
        logger.info("🔍 Validating file name: {}", fileName);
        
        ValidationResultDto result = fileNameValidator.validate(fileName);
        
        if (result.isPassed()) {
            logger.info("✅ File name validation passed");
        } else {
            logger.error("❌ File name validation failed");
        }
        
        return result;
    }

    /**
     * Validate file existence (by checking if content is not empty)
     */
    public ValidationResultDto validateFileExistence(byte[] fileContent) {
        logger.info("🔍 Validating file existence and content");
        
        ValidationResultDto result = fileExistenceValidator.validate(fileContent);
        
        if (result.isPassed()) {
            logger.info("✅ File existence validation passed");
        } else {
            logger.error("❌ File existence validation failed");
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
            logger.error("❌ File size validation failed");
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
            logger.info("✅ File integrity validation passed");
        } else {
            logger.error("❌ File integrity validation failed");
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
            logger.error("❌ ISBN validation failed");
        }
        
        return result;
    }
}
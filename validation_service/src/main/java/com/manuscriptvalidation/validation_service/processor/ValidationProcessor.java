package com.manuscriptvalidation.validation_service.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.dto.ValidationStatus;
import com.manuscriptvalidation.validation_service.enums.ActivityType;
import com.manuscriptvalidation.validation_service.service.ActivityService;
import com.manuscriptvalidation.validation_service.service.S3Service;
import com.manuscriptvalidation.validation_service.service.ValidationService;
import com.manuscriptvalidation.validation_service.service.ValidationStatusService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import java.util.ArrayList;
import java.util.List;

/**
 * ValidationProcessor - Executes the sequential manuscript validation pipeline:
 * 1. Required Metadata Validation
 * 2. ISBN Validation
 * 3. File Extension Validation
 * 4. File Name Validation
 * 5. File Existence Validation (S3 fetch)
 * 6. File Size Validation
 * 7. File Corruption / Integrity Validation
 */
@Component
public class ValidationProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);

    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final ValidationService validationService;
    private final ActivityService activityService;
    private final ValidationStatusService validationStatusService;

    public ValidationProcessor(ObjectMapper objectMapper,
                               S3Service s3Service,
                               ValidationService validationService,
                               ActivityService activityService,
                               ValidationStatusService validationStatusService) {
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
        this.validationService = validationService;
        this.activityService = activityService;
        this.validationStatusService = validationStatusService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

    String message = exchange.getIn().getBody(String.class);

    JsonNode snsEnvelope = objectMapper.readTree(message);

    String innerMessage = (snsEnvelope != null && snsEnvelope.has("Message"))
            ? snsEnvelope.get("Message").asText()
            : message;

    FileUploadedEvent event =
            objectMapper.readValue(innerMessage, FileUploadedEvent.class);

    String requestId = event.getRequestId();

    logger.info("📋 Initializing validation tracking for requestId: {}", requestId);

    activityService.addActivity(
            requestId,
            ActivityType.VALIDATION_STARTED
    );

    validationStatusService.createStatus(requestId);

    exchange.setProperty("EVENT_OBJECT", event);
    exchange.getIn().setHeader("REQUEST_ID", requestId);

    // Collect ALL validation errors
    List<ValidationErrorDto> allErrors = new ArrayList<>();


    // ================================================================
    // 1. REQUIRED METADATA VALIDATION
    // ================================================================

    logger.info("🔍 [1/7] Validating required metadata: {}", requestId);

    validationStatusService.updateRequiredMetadataStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    ValidationResultDto metadataResult =
            validationService.validateMetadata(event);

    if (!metadataResult.isPassed()) {

        allErrors.addAll(metadataResult.getErrors());

        validationStatusService.updateRequiredMetadataStatus(
                requestId,
                ValidationStatus.FAILED
        );

        logger.warn("❌ [1/7] Metadata validation failed");

    } else {

        validationStatusService.updateRequiredMetadataStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [1/7] Metadata validation passed");
    }


    // ================================================================
    // 2. ISBN VALIDATION
    // ================================================================

    logger.info("🔍 [2/7] Validating ISBN for requestId: {}", requestId);

    validationStatusService.updateIsbnStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    ValidationResultDto isbnResult =
            validationService.validateISBN(event.getIsbn());

    if (!isbnResult.isPassed()) {

        allErrors.addAll(isbnResult.getErrors());

        validationStatusService.updateIsbnStatus(
                requestId,
                ValidationStatus.FAILED
        );

        logger.warn("❌ [2/7] ISBN validation failed");

    } else {

        validationStatusService.updateIsbnStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [2/7] ISBN validation passed");
    }


    // ================================================================
    // 3. FILE EXISTENCE IN S3
    // ================================================================

    logger.info(
            "🔍 [3/7] Checking file existence in S3: {}",
            event.getS3Reference()
    );

    validationStatusService.updateFileExistenceStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    logger.info("📌 Raw s3Reference from event: '{}'", event.getS3Reference());

    String fileName = extractFileName(event.getS3Reference());
    
    logger.info("📄 Extracted fileName from s3Reference: '{}'", fileName);

    String s3Path = "ingestion/" + requestId + "/" + fileName;

    logger.info("📂 Download path from S3: {}", s3Path);

    byte[] manuscriptContent = null;

    try {

        logger.info(
                "📥 File exists check: downloading manuscript from S3: {}",
                s3Path
        );

        manuscriptContent =
                s3Service.downloadManuscriptByPath(s3Path);

        ValidationResultDto existenceResult =
                validationService.validateFileExistence(manuscriptContent);

        if (!existenceResult.isPassed()) {

            allErrors.addAll(existenceResult.getErrors());

            validationStatusService.updateFileExistenceStatus(
                    requestId,
                    ValidationStatus.FAILED
            );

            logger.warn("❌ [3/7] File existence validation failed");

            // No file content available for later validations
            setFinalValidationResult(
                    exchange,
                    allErrors,
                    fileName,
                    null
            );

            return;
        }

        validationStatusService.updateFileExistenceStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [3/7] File exists in S3 and was downloaded successfully");

    } catch (Exception e) {

        logger.error(
                "❌ [3/7] File does not exist in S3: {}",
                s3Path,
                e
        );

        ValidationResultDto existenceResult =
                new ValidationResultDto();

        existenceResult.setPassed(false);

        existenceResult.addError(
                new ValidationErrorDto(
                        "FILE_NOT_FOUND",
                        "File does not exist in S3"
                )
        );

        allErrors.addAll(existenceResult.getErrors());

        validationStatusService.updateFileExistenceStatus(
                requestId,
                ValidationStatus.FAILED
        );

        setFinalValidationResult(
                exchange,
                allErrors,
                fileName,
                null
        );

        return;
    }


    // ================================================================
    // 4. FILE EXTENSION
    // ================================================================

    logger.info(
            "🔍 [4/7] Validating file extension: {}",
            fileName
    );

    validationStatusService.updateFileExtensionStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    ValidationResultDto extensionResult =
            validationService.validateFileExtension(
                    event.getS3Reference()
            );

    if (!extensionResult.isPassed()) {

        allErrors.addAll(extensionResult.getErrors());

        validationStatusService.updateFileExtensionStatus(
                requestId,
                ValidationStatus.FAILED
        );

        logger.warn("❌ [4/7] File extension validation failed");

    } else {

        validationStatusService.updateFileExtensionStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [4/7] File extension validation passed");
    }


    // ================================================================
    // 5. FILE NAME
    // ================================================================

    logger.info(
            "🔍 [5/7] Validating file name: {}",
            fileName
    );

    validationStatusService.updateFileNameStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    ValidationResultDto fileNameResult =
            validationService.validateFileName(fileName);

    if (!fileNameResult.isPassed()) {

        allErrors.addAll(fileNameResult.getErrors());

        validationStatusService.updateFileNameStatus(
                requestId,
                ValidationStatus.FAILED
        );

        logger.warn("❌ [5/7] File name validation failed");

    } else {

        validationStatusService.updateFileNameStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [5/7] File name validation passed");
    }


    // ================================================================
    // 6. FILE SIZE
    // ================================================================

    logger.info("🔍 [6/7] Validating file size");

    validationStatusService.updateFileSizeStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    ValidationResultDto sizeResult =
            validationService.validateFileSize(manuscriptContent);

    if (!sizeResult.isPassed()) {

        allErrors.addAll(sizeResult.getErrors());

        validationStatusService.updateFileSizeStatus(
                requestId,
                ValidationStatus.FAILED
        );

        logger.warn("❌ [6/7] File size validation failed");

    } else {

        validationStatusService.updateFileSizeStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [6/7] File size validation passed");
    }


    // ================================================================
    // 7. FILE CORRUPTION / INTEGRITY
    // ================================================================

    logger.info(
            "🔍 [7/7] Validating file integrity & corruption: {}",
            fileName
    );

    validationStatusService.updateFileCorruptionStatus(
            requestId,
            ValidationStatus.PROCESSING
    );

    ValidationResultDto corruptionResult =
            validationService.validateFileCorruption(
                    manuscriptContent,
                    fileName
            );

    if (!corruptionResult.isPassed()) {

        allErrors.addAll(corruptionResult.getErrors());

        validationStatusService.updateFileCorruptionStatus(
                requestId,
                ValidationStatus.FAILED
        );

        logger.warn("❌ [7/7] File corruption validation failed");

    } else {

        validationStatusService.updateFileCorruptionStatus(
                requestId,
                ValidationStatus.SUCCESSFUL
        );

        logger.info("✅ [7/7] File corruption validation passed");
    }


    // ================================================================
    // FINAL RESULT
    // ================================================================

    setFinalValidationResult(
            exchange,
            allErrors,
            fileName,
            manuscriptContent
    );

    if (allErrors.isEmpty()) {

        logger.info(
                "🏆 All 7 validation rules successfully verified for requestId: {}",
                requestId
        );

    } else {

        logger.warn(
                "🚫 Validation failed for requestId: {} with {} error(s)",
                requestId,
                allErrors.size()
        );
    }
}

    private void setFinalValidationResult(
        Exchange exchange,
        List<ValidationErrorDto> allErrors,
        String fileName,
        byte[] manuscriptContent) {

    ValidationResultDto finalResult =
            new ValidationResultDto();

    finalResult.setPassed(allErrors.isEmpty());

    finalResult.setErrors(allErrors);

    exchange.getIn().setHeader(
            "VALIDATION_PASSED",
            allErrors.isEmpty()
    );

    exchange.setProperty(
            "VALIDATION_RESULT",
            finalResult
    );

    exchange.setProperty(
            "FILE_NAME",
            fileName
    );

    exchange.setProperty(
            "FILE_CONTENT",
            manuscriptContent
    );

    if (!allErrors.isEmpty()) {

        exchange.setProperty(
                "FAILED_VALIDATION_NAME",
                "Validation Failed"
        );
    }

    exchange.getIn().setBody(finalResult);
}


    private String extractFileName(String s3Reference) {

    if (s3Reference == null || s3Reference.isBlank()) {
        return "";
    }

    String path = s3Reference.trim();

    int lastSlashIndex = path.lastIndexOf('/');

    if (lastSlashIndex == -1) {
        return path;
    }

    return path.substring(lastSlashIndex + 1);
}
}
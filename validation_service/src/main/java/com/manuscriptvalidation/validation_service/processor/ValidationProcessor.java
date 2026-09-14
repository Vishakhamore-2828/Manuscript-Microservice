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

        FileUploadedEvent event = objectMapper.readValue(innerMessage, FileUploadedEvent.class);
        String requestId = event.getRequestId();

        logger.info("📋 Initializing validation tracking for requestId: {}", requestId);
        activityService.addActivity(requestId, ActivityType.VALIDATION_STARTED);
        validationStatusService.createStatus(requestId);

        exchange.setProperty("EVENT_OBJECT", event);
        exchange.getIn().setHeader("REQUEST_ID", requestId);

        // Step 1: Validate required metadata
        logger.info("🔍 [1/7] Validating required metadata: {}", requestId);
        validationStatusService.updateRequiredMetadataStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto metadataResult = validationService.validateMetadata(event);

        if (!metadataResult.isPassed()) {
            failValidation(exchange, requestId, "Required Metadata Validation", metadataResult);
            validationStatusService.updateRequiredMetadataStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateRequiredMetadataStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [1/7] Metadata validation passed");

        // Step 2: Validate ISBN
        logger.info("🔍 [2/7] Validating ISBN for requestId: {}", requestId);
        validationStatusService.updateIsbnStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto isbnResult = validationService.validateISBN(event.getIsbn());

        if (!isbnResult.isPassed()) {
            failValidation(exchange, requestId, "ISBN Validation", isbnResult);
            validationStatusService.updateIsbnStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateIsbnStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [2/7] ISBN validation passed");

        // Step 3: Validate file extension from S3
        logger.info("🔍 [3/7] Validating file extension from S3: {}", event.getS3Reference());
        validationStatusService.updateFileExtensionStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto extensionResult = validationService.validateFileExtension(event.getS3Reference());

        if (!extensionResult.isPassed()) {
            failValidation(exchange, requestId, "File Extension Validation", extensionResult);
            validationStatusService.updateFileExtensionStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateFileExtensionStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [3/7] File extension validation passed");

        // Step 4: Validate file name
        String fileName = extractFileName(event.getS3Reference());
        logger.info("🔍 [4/7] Validating file name: {}", fileName);
        validationStatusService.updateFileNameStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto fileNameResult = validationService.validateFileName(fileName);

        if (!fileNameResult.isPassed()) {
            failValidation(exchange, requestId, "File Name Validation", fileNameResult);
            validationStatusService.updateFileNameStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateFileNameStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [4/7] File name validation passed");

        // Step 5: Download file content from S3
        logger.info("📥 Downloading manuscript from S3 for requestId: {}", requestId);
        String s3Path = "injection/" + requestId + "/" + fileName;
        byte[] manuscriptContent = s3Service.downloadManuscriptByPath(s3Path);

        // Step 6: Validate file existence
        logger.info("🔍 [5/7] Validating file existence in S3");
        validationStatusService.updateFileExistenceStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto existenceResult = validationService.validateFileExistence(manuscriptContent);

        if (!existenceResult.isPassed()) {
            failValidation(exchange, requestId, "File Existence Validation", existenceResult);
            validationStatusService.updateFileExistenceStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateFileExistenceStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [5/7] File existence validation passed");

        // Step 7: Validate file size
        logger.info("🔍 [6/7] Validating file size");
        validationStatusService.updateFileSizeStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto sizeResult = validationService.validateFileSize(manuscriptContent);

        if (!sizeResult.isPassed()) {
            failValidation(exchange, requestId, "File Size Validation", sizeResult);
            validationStatusService.updateFileSizeStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateFileSizeStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [6/7] File size validation passed");

        // Step 8: Validate file corruption / integrity
        logger.info("🔍 [7/7] Validating file integrity & magic numbers for '{}'", fileName);
        validationStatusService.updateFileCorruptionStatus(requestId, ValidationStatus.PROCESSING);
        ValidationResultDto corruptionResult = validationService.validateFileCorruption(manuscriptContent, fileName);

        if (!corruptionResult.isPassed()) {
            failValidation(exchange, requestId, "File Integrity (Corruption) Validation", corruptionResult);
            validationStatusService.updateFileCorruptionStatus(requestId, ValidationStatus.FAILED);
            return;
        }
        validationStatusService.updateFileCorruptionStatus(requestId, ValidationStatus.SUCCESSFUL);
        logger.info("✅ [7/7] File integrity validation passed");

        // Mark success for downstream Camel routes
        exchange.getIn().setHeader("VALIDATION_PASSED", true);
        exchange.setProperty("FILE_NAME", fileName);
        exchange.setProperty("FILE_CONTENT", manuscriptContent);
        exchange.setProperty("VALIDATION_RESULT", corruptionResult);
        exchange.getIn().setBody(corruptionResult);

        logger.info("🏆 All 7 validation rules successfully verified for requestId: {}", requestId);
    }

    private void failValidation(Exchange exchange,
                                String requestId,
                                String stepName,
                                ValidationResultDto result) {
        logger.warn("🚫 Validation rule failed at '{}' for requestId: {}", stepName, requestId);
        exchange.getIn().setHeader("VALIDATION_PASSED", false);
        exchange.setProperty("FAILED_VALIDATION_NAME", stepName);
        exchange.setProperty("VALIDATION_RESULT", result);
        exchange.getIn().setBody(result);
    }

    private String extractFileName(String s3Reference) {
        if (s3Reference == null || s3Reference.isBlank()) {
            throw new IllegalArgumentException("S3 reference is empty");
        }
        return s3Reference.substring(s3Reference.lastIndexOf("/") + 1);
    }
}
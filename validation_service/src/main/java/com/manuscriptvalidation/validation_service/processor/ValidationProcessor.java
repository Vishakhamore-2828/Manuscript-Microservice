package com.manuscriptvalidation.validation_service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.service.S3Service;
import com.manuscriptvalidation.validation_service.service.ValidationService;
import com.manuscriptvalidation.validation_service.service.SesEmailService;
import com.manuscriptvalidation.validation_service.service.ActivityService;
import com.manuscriptvalidation.validation_service.enums.ActivityType;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

@Component
public class ValidationProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);

    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final ValidationService validationService;
    private final SnsClient snsClient;
    private final ActivityService activityService;
    private final SesEmailService sesEmailService;
    
    @Value("${aws.sns.validation-result-topic-arn}")
    private String validationResultTopicArn;
    
    @Value("${aws.s3.archive-bucket-name}")
    private String archiveBucketName;

    public ValidationProcessor(ObjectMapper objectMapper, 
                               S3Service s3Service,
                               ValidationService validationService,
                               SnsClient snsClient,
                               ActivityService activityService,
                               SesEmailService sesEmailService) {
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
        this.validationService = validationService;
        this.snsClient = snsClient;
        this.activityService = activityService;
        this.sesEmailService = sesEmailService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        
        String message = exchange.getIn().getBody(String.class);
        JsonNode snsEnvelope = objectMapper.readTree(message);
        String innerMessage = snsEnvelope.has("Message") 
            ? snsEnvelope.get("Message").asText() 
            : message;

        FileUploadedEvent event = objectMapper.readValue(innerMessage, FileUploadedEvent.class);
        String requestId = event.getRequestId();

        logger.info("📋 Adding VALIDATION_STARTED activity");
        activityService.addActivity(requestId, ActivityType.VALIDATION_STARTED);

        ValidationResultDto validationResult = null;

        try {
            // Step 1: Validate required metadata
            logger.info("🔍 Starting metadata validation: {}", requestId);
            validationResult = validationService.validateMetadata(event);

            if (!validationResult.isPassed()) {
                logger.error("❌ Metadata validation failed for requestId: {}", requestId);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, validationResult, "Required Metadata Validation");
                
                if (emailSent) {
                    logger.info("✅ Adding EMAIL_SENT activity");
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    logger.info("❌ Adding EMAIL_NOT_SENT activity");
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            logger.info("✅ Metadata validation passed");

            // Step 2: Validate file extension from S3
            logger.info("🔍 Validating file extension from S3: {}", event.getS3Reference());
            ValidationResultDto fileValidation = validationService.validateFileExtension(event.getS3Reference());

            if (!fileValidation.isPassed()) {
                logger.error("❌ File extension validation failed for requestId: {}", requestId);
                validationResult = fileValidation;
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileValidation, "File Extension Validation");
                
                if (emailSent) {
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            logger.info("✅ File extension validation passed");

            // Step 3: Validate file name
            String fileName = extractFileNameFromS3Reference(event.getS3Reference());
            logger.info("🔍 Validating file name: {}", fileName);
            ValidationResultDto fileNameValidation = validationService.validateFileName(fileName);

            if (!fileNameValidation.isPassed()) {
                logger.error("❌ File name validation failed for requestId: {}", requestId);
                validationResult = fileNameValidation;
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileNameValidation, "File Name Validation");
                
                if (emailSent) {
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            logger.info("✅ File name validation passed");

            // Step 4: Download file (only after all validations pass)
            logger.info("📥 Downloading manuscript from S3 for requestId: {}", requestId);
            String s3Path = "injection/" + requestId + "/" + fileName;
            byte[] manuscriptContent = s3Service.downloadManuscriptByPath(s3Path);

            // Step 5: Validate file existence
            logger.info("🔍 Validating file existence");
            ValidationResultDto fileExistenceValidation = validationService.validateFileExistence(manuscriptContent);

            if (!fileExistenceValidation.isPassed()) {
                logger.error("❌ File existence validation failed for requestId: {}", requestId);
                validationResult = fileExistenceValidation;
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileExistenceValidation, "File Existence Validation");
                
                if (emailSent) {
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            logger.info("✅ File existence validation passed");

            // Step 6: Validate file size
            logger.info("🔍 Validating file size");
            ValidationResultDto fileSizeValidation = validationService.validateFileSize(manuscriptContent);

            if (!fileSizeValidation.isPassed()) {
                logger.error("❌ File size validation failed for requestId: {}", requestId);
                validationResult = fileSizeValidation;
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileSizeValidation, "File Size Validation");
                
                if (emailSent) {
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            logger.info("✅ File size validation passed");

            // Step 7: Validate file corruption
            logger.info("🔍 Validating file integrity");
            ValidationResultDto fileCorruptionValidation = validationService.validateFileCorruption(manuscriptContent, fileName);

            if (!fileCorruptionValidation.isPassed()) {
                logger.error("❌ File corruption validation failed for requestId: {}", requestId);
                validationResult = fileCorruptionValidation;
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileCorruptionValidation, "File Integrity (Corruption) Validation");
                
                if (emailSent) {
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            logger.info("✅ File integrity validation passed");

            logger.info("✅ All validations passed for requestId: {}", requestId);
            logger.info("✅ Adding VALIDATION_PASSED activity");
            activityService.addActivity(requestId, ActivityType.VALIDATION_PASSED);

            // Step 8: Archive file
            try {
                String archiveS3Url = archiveValidatedManuscript(requestId, fileName, manuscriptContent);

                logger.info("✅ Adding FILE_ARCHIVED_PASSED activity");
                activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_PASSED);

                // Step 9: Send success email
                boolean emailSent = sendValidationNotification(requestId, event, true, null, null);
                
                if (emailSent) {
                    logger.info("✅ Adding EMAIL_SENT activity");
                    activityService.addActivity(requestId, ActivityType.EMAIL_SENT);
                } else {
                    logger.info("❌ Adding EMAIL_NOT_SENT activity");
                    activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                }

            } catch (Exception archiveError) {
                logger.error("❌ Archive failed: {}", archiveError.getMessage());
                
                logger.info("❌ Adding FILE_ARCHIVED_FAILED activity");
                activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_FAILED);
                
                logger.info("❌ Adding EMAIL_NOT_SENT activity");
                activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                
                throw archiveError;
            }

            // Publish success result to SNS
            publishValidationResultToSNS(requestId, event, validationResult);

            exchange.getIn().setHeader("VALIDATION_PASSED", true);
            exchange.getIn().setHeader("REQUEST_ID", requestId);
            exchange.getIn().setBody(validationResult);

        } catch (Exception e) {
            logger.error("❌ Error during validation: {}", e.getMessage(), e);
            
            logger.info("❌ Adding VALIDATION_FAILED activity (error)");
            activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);
            
            try {
                publishValidationErrorToSNS(requestId, event, e);
            } catch (Exception snsError) {
                logger.error("Failed to publish error to SNS: {}", snsError.getMessage());
            }
            
            throw e;
        }
    }

    /**
     * Send validation notification email
     * @return true if email sent successfully, false if failed
     */
    private boolean sendValidationNotification(String requestId, 
                                              FileUploadedEvent event,
                                              boolean validationPassed,
                                              ValidationResultDto validationResult,
                                              String failedValidationName) {
        try {
            String authorEmail = event.getAuthorEmail();
            
            if (authorEmail == null || authorEmail.isBlank()) {
                logger.warn("⚠️ Author email is missing for requestId: {}", requestId);
                return false;
            }
            
            logger.info("📧 Sending email notification for requestId: {} to {}", requestId, authorEmail);
            
            String subject = validationPassed 
                ? "Manuscript Validation Passed" 
                : "Manuscript Validation Failed";
            
            String emailBody = buildEmailBody(event, validationPassed, validationResult, failedValidationName);
            
            boolean emailSent = sesEmailService.sendEmail(authorEmail, subject, emailBody);
            
            if (emailSent) {
                logger.info("Email successfully sent for requestId: {} to {}", requestId, authorEmail);
            } else {
                logger.warn("Email failed to send for requestId: {} to {}", requestId, authorEmail);
            }
            
            return emailSent;
            
        } catch (Exception e) {
            logger.error("❌ Unexpected error in email notification: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Archive validated manuscript to archive S3 bucket
     * FIXED: Removed "archive/" prefix - let S3Service handle it
     */
    private String archiveValidatedManuscript(String requestId, String fileName, byte[] content) {
        logger.info("📦 Archiving manuscript - RequestID: {}, FileName: {}", requestId, fileName);
        
        String archivePath = requestId + "/" + fileName;
        return s3Service.uploadToArchiveBucket(archiveBucketName, archivePath, content);
    }

    private String buildEmailBody(FileUploadedEvent event, boolean passed, ValidationResultDto validationResult, String failedValidationName) {
        StringBuilder emailBody = new StringBuilder();
        
        emailBody.append("Dear ").append(event.getAuthorId()).append(",\n\n");
        
        if (passed) {
            // Success email - show all validations passed
            emailBody.append("Excellent! Your manuscript has been validated successfully.\n\n");
            emailBody.append("✅ Validation Status: ALL PASSED\n");
            emailBody.append("\n✓ Validations Completed:\n");
            emailBody.append("  1. Required Metadata Validation\n");
            emailBody.append("  2. File Extension Validation\n");
            emailBody.append("  3. File Name Validation\n");
            emailBody.append("  4. File Existence Validation\n");
            emailBody.append("  5. File Size Validation\n");
            emailBody.append("  6. File Integrity (Corruption) Validation\n");
            emailBody.append("\nBook ID: ").append(event.getBookId()).append("\n");
        } else {
            // Failure email - show what passed and what failed
            emailBody.append("Your manuscript validation could not be completed.\n\n");
            emailBody.append("❌ Validation Status: FAILED\n\n");
            
            // Build validation status list - show all validations with pass/fail status
            emailBody.append("Validation Results:\n");
            String[] validations = {
                "Required Metadata Validation",
                "File Extension Validation",
                "File Name Validation",
                "File Existence Validation",
                "File Size Validation",
                "File Integrity (Corruption) Validation"
            };
            
            for (int i = 0; i < validations.length; i++) {
                if (failedValidationName != null && validations[i].equals(failedValidationName)) {
                    // This validation failed
                    emailBody.append("  ❌ ").append(i + 1).append(". ").append(validations[i]).append("\n");
                } else {
                    // This validation passed
                    emailBody.append("  ✅ ").append(i + 1).append(". ").append(validations[i]).append("\n");
                }
            }
            
            emailBody.append("\n");
            
            // Show error details
            if (validationResult != null && !validationResult.getErrors().isEmpty()) {
                emailBody.append("Error Details:\n");
                for (var error : validationResult.getErrors()) {
                    emailBody.append("  • ").append(error.getMessage()).append("\n");
                }
                emailBody.append("\n");
            }
            
            emailBody.append("Book ID: ").append(event.getBookId()).append("\n");
            emailBody.append("Request ID: ").append(event.getRequestId()).append("\n");
        }
        
        emailBody.append("\nBest regards,\n");
        emailBody.append("Manuscript Validation Service\n");
        emailBody.append("Book Platform");
        
        return emailBody.toString();
    }

    private void publishValidationResultToSNS(
        String requestId,
        FileUploadedEvent event,
        ValidationResultDto result) throws Exception {

    ValidationResultMessage resultMessage = new ValidationResultMessage(
        requestId,
        event.getBookId(),
        event.getAuthorId(),
        event.getS3Reference(),
        result.isPassed(),
        result.getErrors().size(),
        result.isPassed() ? "VALIDATION_PASSED" : "VALIDATION_FAILED"
    );

    String messageJson = objectMapper.writeValueAsString(resultMessage);

    PublishRequest publishRequest = PublishRequest.builder()
        .topicArn(validationResultTopicArn)
        .message(messageJson)
        .subject("Manuscript Validation Result - " + requestId)
        .build();

    PublishResponse response = snsClient.publish(publishRequest);
    logger.info("✅ SNS Message ID: {}", response.messageId());
}

    private void publishValidationErrorToSNS(
        String requestId,
        FileUploadedEvent event,
        Exception error) throws Exception {

    ValidationResultMessage errorMessage = new ValidationResultMessage(
        requestId,
        event.getBookId(),
        event.getAuthorId(),
        event.getS3Reference(),
        false,
        1,
        "VALIDATION_ERROR: " + error.getMessage()
    );

    String messageJson = objectMapper.writeValueAsString(errorMessage);

    PublishRequest publishRequest = PublishRequest.builder()
        .topicArn(validationResultTopicArn)
        .message(messageJson)
        .subject("Manuscript Validation Error - " + requestId)
        .build();

    snsClient.publish(publishRequest);
    logger.error("❌ Error published to SNS");
}

    private String extractFileNameFromS3Reference(String s3Reference) {
        if (s3Reference == null || s3Reference.isEmpty()) {
            throw new RuntimeException("S3 reference is empty");
        }
        return s3Reference.substring(s3Reference.lastIndexOf("/") + 1);
    }

    public static class ValidationResultMessage {

    public String requestId;
    public String bookId;
    public String authorId;
    public String s3Reference;
    public boolean validationPassed;
    public int errorCount;
    public String status;

    public ValidationResultMessage(
            String requestId,
            String bookId,
            String authorId,
            String s3Reference,
            boolean validationPassed,
            int errorCount,
            String status) {

        this.requestId = requestId;
        this.bookId = bookId;
        this.authorId = authorId;
        this.s3Reference = s3Reference;
        this.validationPassed = validationPassed;
        this.errorCount = errorCount;
        this.status = status;
    }
}
}
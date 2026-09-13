package com.manuscriptvalidation.validation_service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.dto.ValidationStatus;
import com.manuscriptvalidation.validation_service.service.S3Service;
import com.manuscriptvalidation.validation_service.service.ValidationService;
import com.manuscriptvalidation.validation_service.service.ValidationStatusService;
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
    private final ValidationStatusService validationStatusService;
    
    @Value("${aws.sns.validation-result-topic-arn}")
    private String validationResultTopicArn;
    
    @Value("${aws.s3.archive-bucket-name}")
    private String archiveBucketName;

    public ValidationProcessor(ObjectMapper objectMapper, 
                               S3Service s3Service,
                               ValidationService validationService,
                               SnsClient snsClient,
                               ActivityService activityService,
                               SesEmailService sesEmailService,
                               ValidationStatusService validationStatusService) {
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
        this.validationService = validationService;
        this.snsClient = snsClient;
        this.activityService = activityService;
        this.sesEmailService = sesEmailService;
        this.validationStatusService = validationStatusService;
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

        logger.info("📋 Initializing validation tracking for requestId: {}", requestId);
        activityService.addActivity(requestId, ActivityType.VALIDATION_STARTED);
        validationStatusService.createStatus(requestId);

        ValidationResultDto validationResult = null;

        try {
            // Step 1: Validate required metadata
            logger.info("🔍 Starting metadata validation: {}", requestId);
            validationStatusService.updateRequiredMetadataStatus(requestId, ValidationStatus.PROCESSING);
            validationResult = validationService.validateMetadata(event);

            if (!validationResult.isPassed()) {
                logger.error("❌ Metadata validation failed for requestId: {}", requestId);
                validationStatusService.updateRequiredMetadataStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, validationResult, "Required Metadata Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, validationResult);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(validationResult);
                return;
            }

            validationStatusService.updateRequiredMetadataStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ Metadata validation passed");

            // Step 2: Validate ISBN
            logger.info("🔍 Validating ISBN for requestId: {}", requestId);
            validationStatusService.updateIsbnStatus(requestId, ValidationStatus.PROCESSING);
            ValidationResultDto isbnValidation = validationService.validateISBN(event.getIsbn());

            if (!isbnValidation.isPassed()) {
                logger.error("❌ ISBN validation failed for requestId: {}", requestId);
                validationStatusService.updateIsbnStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, isbnValidation, "ISBN Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, isbnValidation);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(isbnValidation);
                return;
            }

            validationStatusService.updateIsbnStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ ISBN validation passed");

            // Step 3: Validate file extension from S3
            logger.info("🔍 Validating file extension from S3: {}", event.getS3Reference());
            validationStatusService.updateFileExtensionStatus(requestId, ValidationStatus.PROCESSING);
            ValidationResultDto fileValidation = validationService.validateFileExtension(event.getS3Reference());

            if (!fileValidation.isPassed()) {
                logger.error("❌ File extension validation failed for requestId: {}", requestId);
                validationStatusService.updateFileExtensionStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileValidation, "File Extension Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, fileValidation);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(fileValidation);
                return;
            }

            validationStatusService.updateFileExtensionStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ File extension validation passed");

            // Step 4: Validate file name
            String fileName = extractFileNameFromS3Reference(event.getS3Reference());
            logger.info("🔍 Validating file name: {}", fileName);
            validationStatusService.updateFileNameStatus(requestId, ValidationStatus.PROCESSING);
            ValidationResultDto fileNameValidation = validationService.validateFileName(fileName);

            if (!fileNameValidation.isPassed()) {
                logger.error("❌ File name validation failed for requestId: {}", requestId);
                validationStatusService.updateFileNameStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileNameValidation, "File Name Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, fileNameValidation);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(fileNameValidation);
                return;
            }

            validationStatusService.updateFileNameStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ File name validation passed");

            // Step 5: Download file (only after metadata and name validations pass)
            logger.info("📥 Downloading manuscript from S3 for requestId: {}", requestId);
            String s3Path = "injection/" + requestId + "/" + fileName;
            byte[] manuscriptContent = s3Service.downloadManuscriptByPath(s3Path);

            // Step 6: Validate file existence
            logger.info("🔍 Validating file existence");
            validationStatusService.updateFileExistenceStatus(requestId, ValidationStatus.PROCESSING);
            ValidationResultDto fileExistenceValidation = validationService.validateFileExistence(manuscriptContent);

            if (!fileExistenceValidation.isPassed()) {
                logger.error("❌ File existence validation failed for requestId: {}", requestId);
                validationStatusService.updateFileExistenceStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileExistenceValidation, "File Existence Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, fileExistenceValidation);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(fileExistenceValidation);
                return;
            }

            validationStatusService.updateFileExistenceStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ File existence validation passed");

            // Step 7: Validate file size
            logger.info("🔍 Validating file size");
            validationStatusService.updateFileSizeStatus(requestId, ValidationStatus.PROCESSING);
            ValidationResultDto fileSizeValidation = validationService.validateFileSize(manuscriptContent);

            if (!fileSizeValidation.isPassed()) {
                logger.error("❌ File size validation failed for requestId: {}", requestId);
                validationStatusService.updateFileSizeStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileSizeValidation, "File Size Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, fileSizeValidation);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(fileSizeValidation);
                return;
            }

            validationStatusService.updateFileSizeStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ File size validation passed");

            // Step 8: Validate file corruption (integrity)
            logger.info("🔍 Validating file integrity");
            validationStatusService.updateFileCorruptionStatus(requestId, ValidationStatus.PROCESSING);
            ValidationResultDto fileCorruptionValidation = validationService.validateFileCorruption(manuscriptContent, fileName);

            if (!fileCorruptionValidation.isPassed()) {
                logger.error("❌ File corruption validation failed for requestId: {}", requestId);
                validationStatusService.updateFileCorruptionStatus(requestId, ValidationStatus.FAILED);
                activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

                boolean emailSent = sendValidationNotification(requestId, event, false, fileCorruptionValidation, "File Integrity (Corruption) Validation");
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

                publishValidationResultToSNS(requestId, event, fileCorruptionValidation);
                exchange.getIn().setHeader("VALIDATION_PASSED", false);
                exchange.getIn().setHeader("REQUEST_ID", requestId);
                exchange.getIn().setBody(fileCorruptionValidation);
                return;
            }

            validationStatusService.updateFileCorruptionStatus(requestId, ValidationStatus.SUCCESSFUL);
            logger.info("✅ File integrity validation passed");

            logger.info("✅ All validations passed for requestId: {}", requestId);
            logger.info("✅ Adding VALIDATION_PASSED activity");
            activityService.addActivity(requestId, ActivityType.VALIDATION_PASSED);

            // Step 9: Archive file (ONLY reached when all validations passed!)
            try {
                String archiveS3Url = archiveValidatedManuscript(requestId, fileName, manuscriptContent);

                logger.info("✅ Adding FILE_ARCHIVED_PASSED activity");
                activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_PASSED);

                // Step 10: Send success email
                boolean emailSent = sendValidationNotification(requestId, event, true, null, null);
                activityService.addActivity(requestId, emailSent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);

            } catch (Exception archiveError) {
                logger.error("❌ Archive failed: {}", archiveError.getMessage());
                
                logger.info("❌ Adding FILE_ARCHIVED_FAILED activity");
                activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_FAILED);
                activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
                
                throw archiveError;
            }

            // Publish success result to SNS
            publishValidationResultToSNS(requestId, event, fileCorruptionValidation);

            exchange.getIn().setHeader("VALIDATION_PASSED", true);
            exchange.getIn().setHeader("REQUEST_ID", requestId);
            exchange.getIn().setBody(fileCorruptionValidation);

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
     */
    private String archiveValidatedManuscript(String requestId, String fileName, byte[] content) {
        logger.info("📦 Archiving manuscript - RequestID: {}, FileName: {}", requestId, fileName);
        
        String archivePath = requestId + "/" + fileName;
        return s3Service.uploadToArchiveBucket(archiveBucketName, archivePath, content);
    }

    private String buildEmailBody(FileUploadedEvent event, boolean passed, ValidationResultDto validationResult, String failedValidationName) {
        StringBuilder emailBody = new StringBuilder();
        
        emailBody.append("Dear ").append(event.getAuthorId()).append(",\n\n");
        
        String[] validations = {
            "Required Metadata Validation",
            "ISBN Validation",
            "File Extension Validation",
            "File Name Validation",
            "File Existence Validation",
            "File Size Validation",
            "File Integrity (Corruption) Validation"
        };
        
        if (passed) {
            emailBody.append("Excellent! Your manuscript has been validated successfully.\n\n");
            emailBody.append("✅ Validation Status: ALL PASSED\n");
            emailBody.append("\n✓ Validations Completed:\n");
            for (int i = 0; i < validations.length; i++) {
                emailBody.append("  ").append(i + 1).append(". ").append(validations[i]).append("\n");
            }
            emailBody.append("\nBook ID: ").append(event.getBookId()).append("\n");
        } else {
            emailBody.append("Your manuscript validation could not be completed.\n\n");
            emailBody.append("❌ Validation Status: FAILED\n\n");
            
            emailBody.append("Validation Results:\n");
            for (int i = 0; i < validations.length; i++) {
                if (failedValidationName != null && validations[i].equals(failedValidationName)) {
                    emailBody.append("  ❌ ").append(i + 1).append(". ").append(validations[i]).append("\n");
                } else {
                    emailBody.append("  ✅ ").append(i + 1).append(". ").append(validations[i]).append("\n");
                }
            }
            
            emailBody.append("\n");
            
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

    private void publishValidationResultToSNS(String requestId, 
                                              FileUploadedEvent event,
                                              ValidationResultDto result) throws Exception {
        
        ValidationResultMessage resultMessage = new ValidationResultMessage(
            requestId,
            event.getBookId(),
            event.getAuthorId(),
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

    private void publishValidationErrorToSNS(String requestId,
                                             FileUploadedEvent event,
                                             Exception error) throws Exception {
        
        ValidationResultMessage errorMessage = new ValidationResultMessage(
            requestId,
            event.getBookId(),
            event.getAuthorId(),
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
        public boolean validationPassed;
        public int errorCount;
        public String status;

        public ValidationResultMessage(String requestId, String bookId, String authorId,
                                      boolean validationPassed, int errorCount, String status) {
            this.requestId = requestId;
            this.bookId = bookId;
            this.authorId = authorId;
            this.validationPassed = validationPassed;
            this.errorCount = errorCount;
            this.status = status;
        }
    }
}
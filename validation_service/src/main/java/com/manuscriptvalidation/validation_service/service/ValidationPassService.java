package com.manuscriptvalidation.validation_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.enums.ActivityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * ValidationPassService - Handles the success lifecycle after all 7 validations pass:
 * 1. Records VALIDATION_PASSED in MongoDB activities
 * 2. Archives validated manuscript to S3 archive bucket
 * 3. Records FILE_ARCHIVED_PASSED in MongoDB activities
 * 4. Publishes VALIDATION_PASSED event notification to AWS SNS
 */
@Service
public class ValidationPassService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationPassService.class);

    private final S3Service s3Service;
    private final ActivityService activityService;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.archive-bucket-name}")
    private String archiveBucketName;

    @Value("${aws.sns.validation-result-topic-arn}")
    private String validationResultTopicArn;

    public ValidationPassService(S3Service s3Service,
                                 ActivityService activityService,
                                 SnsClient snsClient,
                                 ObjectMapper objectMapper) {
        this.s3Service = s3Service;
        this.activityService = activityService;
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
    }

    public void handleValidationPass(String requestId,
                                     FileUploadedEvent event,
                                     String fileName,
                                     byte[] manuscriptContent,
                                     ValidationResultDto validationResult) {
        logger.info("🎉 Processing VALIDATION_PASS lifecycle for request: {}", requestId);

        // 1. Record validation passed activity
        activityService.addActivity(requestId, ActivityType.VALIDATION_PASSED);

        // 2. Archive to S3
        try {
            String archivePath = requestId + "/" + fileName;
            logger.info("📦 Archiving manuscript to S3 bucket '{}' at path: {}", archiveBucketName, archivePath);
            s3Service.uploadToArchiveBucket(archiveBucketName, archivePath, manuscriptContent);
            
            activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_PASSED);
            logger.info("✅ Manuscript successfully archived to S3 for request: {}", requestId);
        } catch (Exception e) {
            logger.error("❌ Failed to archive manuscript to S3 for request: {} - Error: {}", requestId, e.getMessage(), e);
            activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_FAILED);
            throw e;
        }

        // 3. Publish result to SNS topic
        publishValidationResultToSNS(requestId, event, true, 0, "VALIDATION_PASSED");
    }

    private void publishValidationResultToSNS(String requestId,
                                              FileUploadedEvent event,
                                              boolean passed,
                                              int errorCount,
                                              String status) {
        try {
            var message = new ValidationResultSnsPayload(
                    requestId,
                    event.getBookId(),
                    event.getAuthorId(),
                    passed,
                    errorCount,
                    status
            );

            String messageJson = objectMapper.writeValueAsString(message);
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(validationResultTopicArn)
                    .message(messageJson)
                    .subject("Manuscript Validation Result - " + requestId)
                    .build();

            PublishResponse response = snsClient.publish(publishRequest);
            logger.info("📢 Published VALIDATION_PASSED event to SNS (MessageId: {})", response.messageId());
        } catch (Exception e) {
            logger.error("❌ Failed to publish validation pass event to SNS: {}", e.getMessage(), e);
        }
    }

    public record ValidationResultSnsPayload(
            String requestId,
            String bookId,
            String authorId,
            boolean validationPassed,
            int errorCount,
            String status
    ) {}
}

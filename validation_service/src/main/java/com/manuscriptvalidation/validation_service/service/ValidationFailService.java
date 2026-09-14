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
 * ValidationFailService - Handles rejection lifecycle when any validation rule fails:
 * 1. Records VALIDATION_FAILED in MongoDB activities
 * 2. Ensures rejected / corrupted files are NEVER archived to S3
 * 3. Publishes VALIDATION_FAILED notification to AWS SNS
 */
@Service
public class ValidationFailService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationFailService.class);

    private final ActivityService activityService;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.validation-result-topic-arn}")
    private String validationResultTopicArn;

    public ValidationFailService(ActivityService activityService,
                                 SnsClient snsClient,
                                 ObjectMapper objectMapper) {
        this.activityService = activityService;
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
    }

    public void handleValidationFail(String requestId,
                                     FileUploadedEvent event,
                                     ValidationResultDto validationResult) {
        logger.warn("🚫 Processing VALIDATION_FAIL lifecycle for request: {} (Errors: {})",
                requestId, validationResult != null ? validationResult.getErrors().size() : 1);

        // 1. Record validation failed activity in MongoDB
        activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);

        // 2. Publish failure event to SNS topic
        int errorCount = (validationResult != null) ? validationResult.getErrors().size() : 1;
        publishValidationResultToSNS(requestId, event, false, errorCount, "VALIDATION_FAILED");
    }

    private void publishValidationResultToSNS(String requestId,
                                              FileUploadedEvent event,
                                              boolean passed,
                                              int errorCount,
                                              String status) {
        try {
            var message = new ValidationResultSnsPayload(
                    requestId,
                    (event != null) ? event.getBookId() : "UNKNOWN",
                    (event != null) ? event.getAuthorId() : "UNKNOWN",
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
            logger.info("📢 Published VALIDATION_FAILED event to SNS (MessageId: {})", response.messageId());
        } catch (Exception e) {
            logger.error("❌ Failed to publish validation fail event to SNS: {}", e.getMessage(), e);
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

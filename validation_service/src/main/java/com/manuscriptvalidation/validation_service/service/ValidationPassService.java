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
 * 1. Records VALIDATION_PASSED in MongoDB
 * 2. Archives validated manuscript to S3 archive bucket
 * 3. Records the canonical archive reference and FILE_ARCHIVED_PASSED in MongoDB
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

    public void handleValidationPass(
            String requestId,
            FileUploadedEvent event,
            String fileName,
            byte[] manuscriptContent,
            ValidationResultDto validationResult) {
        if (validationResult == null || !validationResult.isPassed()) {
            throw new IllegalStateException(
                    "Cannot archive manuscript before all validations pass for requestId=" + requestId);
        }

        logger.info("Validation passed for requestId={}", requestId);
        activityService.addActivity(requestId, ActivityType.VALIDATION_PASSED);

        String archivePath = requestId + "/" + fileName;
        String s3Reference;

        try {
            s3Reference = s3Service.uploadToArchiveBucket(
                    archiveBucketName,
                    archivePath,
                    manuscriptContent
            );
        } catch (RuntimeException e) {
            logger.error("Failed to archive manuscript for requestId={}: {}",
                    requestId, e.getMessage(), e);
            activityService.addActivity(requestId, ActivityType.FILE_ARCHIVED_FAILED);
            throw e;
        }
        activityService.recordSuccessfulArchive(requestId, s3Reference);

        logger.info("Canonical s3Reference={}", s3Reference);
        publishValidationResultToSNS(
                requestId,
                event,
                true,
                0,
                "VALIDATION_PASSED",
                s3Reference
        );
    }

    private void publishValidationResultToSNS(
            String requestId,
            FileUploadedEvent event,
            boolean passed,
            int errorCount,
            String status,
            String s3Reference) {
        var message = new ValidationResultSnsPayload(
                requestId,
                event.getBookId(),
                event.getAuthorId(),
                passed,
                errorCount,
                status,
                s3Reference
        );

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize validation pass event", e);
        }

        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(validationResultTopicArn)
                .message(messageJson)
                .subject("Manuscript Validation Result - " + requestId)
                .build();

        logger.info("Publishing validation result to SNS topic={}", validationResultTopicArn);
        PublishResponse response = snsClient.publish(publishRequest);
        logger.info("Published validation result to SNS, MessageId={}", response.messageId());
    }

    public record ValidationResultSnsPayload(
            String requestId,
            String bookId,
            String authorId,
            boolean validationPassed,
            int errorCount,
            String status,
            String s3Reference
    ) {}
}

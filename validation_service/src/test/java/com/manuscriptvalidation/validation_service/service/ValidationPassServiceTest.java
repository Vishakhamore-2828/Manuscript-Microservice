package com.manuscriptvalidation.validation_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.enums.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidationPassServiceTest {

    private static final String ARCHIVE_BUCKET = "archive-bucket";
    private static final String TOPIC_ARN =
            "arn:aws:sns:ap-south-1:123456789012:manuscript-validation-result";

    private S3Service s3Service;
    private ActivityService activityService;
    private SnsClient snsClient;
    private ObjectMapper objectMapper;
    private ValidationPassService validationPassService;

    @BeforeEach
    void setUp() {
        s3Service = mock(S3Service.class);
        activityService = mock(ActivityService.class);
        snsClient = mock(SnsClient.class);
        objectMapper = new ObjectMapper();
        validationPassService =
                new ValidationPassService(s3Service, activityService, snsClient, objectMapper);

        ReflectionTestUtils.setField(validationPassService, "archiveBucketName", ARCHIVE_BUCKET);
        ReflectionTestUtils.setField(validationPassService, "validationResultTopicArn", TOPIC_ARN);
    }

    @Test
    void archivesTracksAndPublishesCanonicalPayloadInOrder() throws Exception {
        FileUploadedEvent event = new FileUploadedEvent();
        event.setRequestId("REQ-0022");
        event.setBookId("BOOK-0022");
        event.setAuthorId("Auth-0001");
        event.setS3Reference("ingestion/REQ-0022/Original-Book.EPUB");

        String archivePath = "REQ-0022/Original-Book.EPUB";
        String canonicalReference = ARCHIVE_BUCKET + "/" + archivePath;
        byte[] manuscriptContent = new byte[]{1, 2, 3};
        when(s3Service.uploadToArchiveBucket(ARCHIVE_BUCKET, archivePath, manuscriptContent))
                .thenReturn(canonicalReference);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("message-123").build());
        ValidationResultDto validationResult = new ValidationResultDto();
        validationResult.setPassed(true);

        validationPassService.handleValidationPass(
                "REQ-0022",
                event,
                "Original-Book.EPUB",
                manuscriptContent,
                validationResult);

        InOrder inOrder = inOrder(s3Service, activityService, snsClient);
        inOrder.verify(activityService)
                .addActivity("REQ-0022", ActivityType.VALIDATION_PASSED);
        inOrder.verify(s3Service)
                .uploadToArchiveBucket(ARCHIVE_BUCKET, archivePath, manuscriptContent);
        inOrder.verify(activityService)
                .recordSuccessfulArchive("REQ-0022", canonicalReference);

        ArgumentCaptor<PublishRequest> publishCaptor =
                ArgumentCaptor.forClass(PublishRequest.class);
        inOrder.verify(snsClient).publish(publishCaptor.capture());

        PublishRequest publishRequest = publishCaptor.getValue();
        JsonNode payload = objectMapper.readTree(publishRequest.message());
        assertEquals(TOPIC_ARN, publishRequest.topicArn());
        assertEquals(7, payload.size());
        assertEquals("REQ-0022", payload.get("requestId").asText());
        assertEquals("BOOK-0022", payload.get("bookId").asText());
        assertEquals("Auth-0001", payload.get("authorId").asText());
        assertEquals(true, payload.get("validationPassed").asBoolean());
        assertEquals(0, payload.get("errorCount").asInt());
        assertEquals("VALIDATION_PASSED", payload.get("status").asText());
        assertEquals(canonicalReference, payload.get("s3Reference").asText());
        assertFalse(payload.has("bookName"));
        assertFalse(payload.has("authorEmail"));
        assertFalse(payload.has("s3Bucket"));
        assertFalse(payload.has("s3Key"));
    }

    @Test
    void doesNotTrackSuccessOrPublishWhenArchiveFails() {
        when(s3Service.uploadToArchiveBucket(any(), any(), any()))
                .thenThrow(new RuntimeException("upload failed"));
        ValidationResultDto validationResult = new ValidationResultDto();
        validationResult.setPassed(true);

        assertThrows(RuntimeException.class, () ->
                validationPassService.handleValidationPass(
                        "REQ-0022",
                        new FileUploadedEvent(),
                        "Original-Book.EPUB",
                        new byte[]{1},
                        validationResult));

        verify(activityService, never()).recordSuccessfulArchive(any(), any());
        verify(snsClient, never()).publish(any(PublishRequest.class));
    }

    @Test
    void doesNotArchiveWhenFinalValidationResultHasNotPassed() {
        ValidationResultDto failedResult = new ValidationResultDto();
        failedResult.setPassed(false);

        assertThrows(IllegalStateException.class, () ->
                validationPassService.handleValidationPass(
                        "REQ-0022",
                        new FileUploadedEvent(),
                        "Original-Book.EPUB",
                        new byte[]{1},
                        failedResult));

        verify(s3Service, never()).uploadToArchiveBucket(any(), any(), any());
        verify(activityService, never()).addActivity(any(), any());
        verify(activityService, never()).recordSuccessfulArchive(any(), any());
        verify(snsClient, never()).publish(any(PublishRequest.class));
    }
}

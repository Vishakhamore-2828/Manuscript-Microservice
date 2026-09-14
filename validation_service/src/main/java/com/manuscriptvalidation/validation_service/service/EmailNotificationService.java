package com.manuscriptvalidation.validation_service.service;

import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.enums.ActivityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * EmailNotificationService - Constructs and dispatches validation notifications via Amazon SES
 * Tracks EMAIL_SENT / EMAIL_NOT_SENT in MongoDB activities
 */
@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private static final List<String> VALIDATION_STEPS = List.of(
            "Required Metadata Validation",
            "ISBN Validation",
            "File Extension Validation",
            "File Name Validation",
            "File Existence Validation",
            "File Size Validation",
            "File Integrity (Corruption) Validation"
    );

    private static final Predicate<String> IS_BLANK = s -> s == null || s.isBlank();

    private final SesEmailService sesEmailService;
    private final ActivityService activityService;

    public EmailNotificationService(SesEmailService sesEmailService,
                                    ActivityService activityService) {
        this.sesEmailService = sesEmailService;
        this.activityService = activityService;
    }

    /**
     * Send email notification for passed validation
     */
    public boolean sendPassNotification(String requestId, FileUploadedEvent event) {
        if (event == null || IS_BLANK.test(event.getAuthorEmail())) {
            logger.warn("⚠️ Author email is missing for request: {}", requestId);
            activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
            return false;
        }

        String recipient = event.getAuthorEmail();
        String subject = "Manuscript Validation Passed";
        String body = buildPassEmailBody(event);

        logger.info("📧 Sending PASS notification email for request: {} to {}", requestId, recipient);
        boolean sent = sesEmailService.sendEmail(recipient, subject, body);
        activityService.addActivity(requestId, sent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);
        return sent;
    }

    /**
     * Send email notification for failed validation
     */
    public boolean sendFailNotification(String requestId,
                                        FileUploadedEvent event,
                                        ValidationResultDto validationResult,
                                        String failedValidationName) {
        if (event == null || IS_BLANK.test(event.getAuthorEmail())) {
            logger.warn("⚠️ Author email is missing for request: {}", requestId);
            activityService.addActivity(requestId, ActivityType.EMAIL_NOT_SENT);
            return false;
        }

        String recipient = event.getAuthorEmail();
        String subject = "Manuscript Validation Failed";
        String body = buildFailEmailBody(event, validationResult, failedValidationName);

        logger.info("📧 Sending FAIL notification email for request: {} to {}", requestId, recipient);
        boolean sent = sesEmailService.sendEmail(recipient, subject, body);
        activityService.addActivity(requestId, sent ? ActivityType.EMAIL_SENT : ActivityType.EMAIL_NOT_SENT);
        return sent;
    }

    private String buildPassEmailBody(FileUploadedEvent event) {
        StringBuilder email = new StringBuilder();
        email.append("Dear ").append(event.getAuthorId()).append(",\n\n")
                .append("Excellent! Your manuscript has been validated successfully.\n\n")
                .append("✅ Validation Status: ALL PASSED\n\n")
                .append("✓ Validations Completed:\n");

        // Format all completed steps using Stream API
        IntStream.range(0, VALIDATION_STEPS.size())
                .forEach(i -> email.append("  ").append(i + 1).append(". ").append(VALIDATION_STEPS.get(i)).append("\n"));

        email.append("\nBook ID: ").append(event.getBookId()).append("\n")
                .append("Request ID: ").append(event.getRequestId()).append("\n\n")
                .append("Best regards,\n")
                .append("Manuscript Validation Service\n")
                .append("Book Platform");

        return email.toString();
    }

    private String buildFailEmailBody(FileUploadedEvent event,
                                      ValidationResultDto validationResult,
                                      String failedValidationName) {
        StringBuilder email = new StringBuilder();
        email.append("Dear ").append(event.getAuthorId()).append(",\n\n")
                .append("Your manuscript validation could not be completed.\n\n")
                .append("❌ Validation Status: FAILED\n\n")
                .append("Validation Results:\n");

        // Render pass/fail badges using Stream API
        IntStream.range(0, VALIDATION_STEPS.size())
                .forEach(i -> {
                    String step = VALIDATION_STEPS.get(i);
                    boolean isFailedStep = Objects.equals(step, failedValidationName);
                    String badge = isFailedStep ? "  ❌ " : "  ✅ ";
                    email.append(badge).append(i + 1).append(". ").append(step).append("\n");
                });

        email.append("\n");

        // Render error details using Streams
        if (validationResult != null && validationResult.getErrors() != null && !validationResult.getErrors().isEmpty()) {
            email.append("Error Details:\n");
            validationResult.getErrors().stream()
                    .map(ValidationErrorDto::getMessage)
                    .filter(Objects::nonNull)
                    .forEach(msg -> email.append("  • ").append(msg).append("\n"));
            email.append("\n");
        }

        email.append("Book ID: ").append(event.getBookId()).append("\n")
                .append("Request ID: ").append(event.getRequestId()).append("\n\n")
                .append("Best regards,\n")
                .append("Manuscript Validation Service\n")
                .append("Book Platform");

        return email.toString();
    }
}

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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class EmailNotificationService {

    private static final Logger logger =
            LoggerFactory.getLogger(EmailNotificationService.class);

    private static final List<String> VALIDATION_STEPS = List.of(
            "Required Metadata Validation",
            "ISBN Validation",
            "File Extension Validation",
            "File Name Validation",
            "File Existence Validation",
            "File Size Validation",
            "File Integrity Validation"
    );

    private static final Predicate<String> IS_BLANK =
            s -> s == null || s.isBlank();

    private final SesEmailService sesEmailService;
    private final ActivityService activityService;

    public EmailNotificationService(
            SesEmailService sesEmailService,
            ActivityService activityService) {

        this.sesEmailService = sesEmailService;
        this.activityService = activityService;
    }

    /**
     * Send email notification for passed validation.
     */
    public boolean sendPassNotification(
            String requestId,
            FileUploadedEvent event) {

        if (event == null || IS_BLANK.test(event.getAuthorEmail())) {
            logger.warn("Author email is missing for request: {}", requestId);

            activityService.addActivity(
                    requestId,
                    ActivityType.EMAIL_NOT_SENT
            );

            return false;
        }

        String recipient = event.getAuthorEmail();
        String subject = "Manuscript Validation Passed";
        String body = buildPassEmailBody(event);

        logger.info(
                "Sending PASS notification email for request: {} to {}",
                requestId,
                recipient
        );

        boolean sent =
                sesEmailService.sendEmail(
                        recipient,
                        subject,
                        body
                );

        activityService.addActivity(
                requestId,
                sent
                        ? ActivityType.EMAIL_SENT
                        : ActivityType.EMAIL_NOT_SENT
        );

        return sent;
    }

    /**
     * Send email notification for failed validation.
     */
    public boolean sendFailNotification(
            String requestId,
            FileUploadedEvent event,
            ValidationResultDto validationResult,
            String failedValidationName) {

        if (event == null || IS_BLANK.test(event.getAuthorEmail())) {
            logger.warn("Author email is missing for request: {}", requestId);

            activityService.addActivity(
                    requestId,
                    ActivityType.EMAIL_NOT_SENT
            );

            return false;
        }

        String recipient = event.getAuthorEmail();
        String subject = "Manuscript Validation Failed";

        String body =
                buildFailEmailBody(
                        event,
                        validationResult
                );

        logger.info(
                "Sending FAIL notification email for request: {} to {}",
                requestId,
                recipient
        );

        boolean sent =
                sesEmailService.sendEmail(
                        recipient,
                        subject,
                        body
                );

        activityService.addActivity(
                requestId,
                sent
                        ? ActivityType.EMAIL_SENT
                        : ActivityType.EMAIL_NOT_SENT
        );

        return sent;
    }

    /**
     * Builds email body for successful validation.
     */
    private String buildPassEmailBody(
            FileUploadedEvent event) {

        StringBuilder email = new StringBuilder();

        email.append("Dear ")
                .append(event.getAuthorId())
                .append(",\n\n")

                .append("Your manuscript has successfully completed all validation checks.\n\n")

                .append("Validation Status: PASSED\n\n")

                .append("Validations Completed:\n");

        IntStream.range(
                        0,
                        VALIDATION_STEPS.size()
                )
                .forEach(i ->
                        email.append("    ")
                                .append(i + 1)
                                .append(". ")
                                .append(VALIDATION_STEPS.get(i))
                                .append("\n")
                );

        email.append("\n")
                .append("Book ID: ")
                .append(event.getBookId())
                .append("\n")

                .append("Request ID: ")
                .append(event.getRequestId())
                .append("\n\n")

                .append("Your manuscript has successfully passed the validation process ")
                .append("and can proceed to the next stage.\n\n")

                .append("Best regards,\n")
                .append("Manuscript Validation Service\n")
                .append("Book Platform");

        return email.toString();
    }

    /**
     * Builds email body for failed validation.
     */
    private String buildFailEmailBody(
            FileUploadedEvent event,
            ValidationResultDto validationResult) {

        StringBuilder email = new StringBuilder();

        Set<String> failedValidationCodes =
                validationResult != null
                        && validationResult.getErrors() != null
                        ? validationResult.getErrors()
                                .stream()
                                .map(ValidationErrorDto::getCode)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                        : Set.of();

        email.append("Dear ")
                .append(event.getAuthorId())
                .append(",\n\n")

                .append("Your manuscript validation could not be completed successfully.\n\n")

                .append("Validation Status: FAILED\n\n");

        /*
         * Validations that passed.
         */
        email.append("Validations Passed:\n");

        IntStream.range(
                        0,
                        VALIDATION_STEPS.size()
                )
                .filter(i ->
                        !isValidationFailed(
                                VALIDATION_STEPS.get(i),
                                failedValidationCodes
                        )
                )
                .forEach(i ->
                        email.append("    ")
                                .append(i + 1)
                                .append(". ")
                                .append(VALIDATION_STEPS.get(i))
                                .append("\n")
                );

        /*
         * Validations that failed.
         */
        email.append("\n")
                .append("Validations Requiring Attention:\n");

        IntStream.range(
                        0,
                        VALIDATION_STEPS.size()
                )
                .filter(i ->
                        isValidationFailed(
                                VALIDATION_STEPS.get(i),
                                failedValidationCodes
                        )
                )
                .forEach(i ->
                        email.append("    ")
                                .append(i + 1)
                                .append(". ")
                                .append(VALIDATION_STEPS.get(i))
                                .append("\n")
                );

        /*
         * Validation issues explained in author-friendly language.
         */
        if (validationResult != null
                && validationResult.getErrors() != null
                && !validationResult.getErrors().isEmpty()) {

            email.append("\n")
                    .append("Validation Issues Identified:\n\n");

            validationResult.getErrors()
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(error -> {

                        String validationName =
                                getValidationName(error.getCode());

                        email.append(validationName)
                                .append("\n")
                                .append(getAuthorFriendlyMessage(error))
                                .append("\n\n");
                    });
        }

        email.append("Book ID: ")
                .append(event.getBookId())
                .append("\n")

                .append("Request ID: ")
                .append(event.getRequestId())
                .append("\n\n")

                .append("Please review the above issues and upload a corrected manuscript.\n\n")

                .append("Best regards,\n")
                .append("Manuscript Validation Service\n")
                .append("Book Platform");

        return email.toString();
    }

    /**
     * Maps validation error codes to validation names.
     */
    private String getValidationName(String errorCode) {

        if (errorCode == null) {
            return "Validation";
        }

        return switch (errorCode) {

            case "MISSING_METADATA" ->
                    "Required Metadata Validation";

            case "INVALID_ISBN" ->
                    "ISBN Validation";

            case "INVALID_FILE_EXTENSION",
                 "NO_FILE_EXTENSION" ->
                    "File Extension Validation";

            case "INVALID_FILE_NAME",
                 "MISSING_FILE_NAME" ->
                    "File Name Validation";

            case "FILE_NOT_FOUND" ->
                    "File Existence Validation";

            case "FILE_SIZE_EXCEEDED",
                 "INVALID_FILE_SIZE" ->
                    "File Size Validation";

            case "CORRUPTED_FILE" ->
                    "File Integrity Validation";

            default ->
                    "Validation";
        };
    }

    /**
     * Determines which validation failed.
     */
    private boolean isValidationFailed(
            String validationStep,
            Set<String> failedValidationCodes) {

        return switch (validationStep) {

            case "Required Metadata Validation" ->
                    failedValidationCodes.contains("MISSING_METADATA");

            case "ISBN Validation" ->
                    failedValidationCodes.contains("INVALID_ISBN");

            case "File Extension Validation" ->
                    failedValidationCodes.contains("INVALID_FILE_EXTENSION")
                            || failedValidationCodes.contains("NO_FILE_EXTENSION");

            case "File Name Validation" ->
                    failedValidationCodes.contains("INVALID_FILE_NAME")
                            || failedValidationCodes.contains("MISSING_FILE_NAME");

            case "File Existence Validation" ->
                    failedValidationCodes.contains("FILE_NOT_FOUND");

            case "File Size Validation" ->
                    failedValidationCodes.contains("FILE_SIZE_EXCEEDED")
                            || failedValidationCodes.contains("INVALID_FILE_SIZE");

            case "File Integrity Validation" ->
                    failedValidationCodes.contains("CORRUPTED_FILE");

            default ->
                    false;
        };
    }

    /**
     * Converts technical validation messages into author-friendly messages.
     */
    private String getAuthorFriendlyMessage(
            ValidationErrorDto error) {

        if (error == null || error.getCode() == null) {
            return "The manuscript could not be validated for this requirement.";
        }

        return switch (error.getCode()) {

            case "FILE_SIZE_EXCEEDED" ->
                    "The manuscript exceeds the maximum permitted file size. "
                            + "Please reduce the file size and upload it again.";

            case "CORRUPTED_FILE" ->
                    "The uploaded manuscript appears to be corrupted "
                            + "and could not be validated. Please upload a valid copy of the file.";

            case "INVALID_ISBN" ->
                    "The ISBN provided for the manuscript is invalid. "
                            + "Please verify the ISBN and try again.";

            case "MISSING_METADATA" ->
                    "Some required manuscript information is missing. "
                            + "Please provide all required details and upload the manuscript again.";

            case "INVALID_FILE_EXTENSION",
                 "NO_FILE_EXTENSION" ->
                    "The manuscript file format is not supported. "
                            + "Please upload the manuscript in PDF, DOCX, or EPUB format.";

            case "INVALID_FILE_NAME",
                 "MISSING_FILE_NAME" ->
                    "The manuscript file name is invalid. "
                            + "Please rename the file according to the required naming rules and upload it again.";

            case "FILE_NOT_FOUND" ->
                    "The manuscript file could not be found in the document storage. "
                            + "Please upload the manuscript again.";

            default ->
                    error.getMessage() != null
                            ? error.getMessage()
                            : "The manuscript could not be validated for this requirement.";
        };
    }
}
package com.manuscriptvalidation.validation_service.route;

import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import com.manuscriptvalidation.validation_service.processor.ValidationProcessor;
import com.manuscriptvalidation.validation_service.service.EmailNotificationService;
import com.manuscriptvalidation.validation_service.service.ValidationFailService;
import com.manuscriptvalidation.validation_service.service.ValidationPassService;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ValidationRoute - Orchestrates manuscript validation, pass/fail branching, and email notification subroutes
 * 
 * Camel Routes Architecture:
 * 1. Main SQS Consumer Route ("validation-service-route") -> SQS -> direct:validate-manuscript
 * 2. Validation Processing Route ("validate-manuscript-route") -> ValidationProcessor -> choice (pass / fail)
 * 3. Validation Pass Route ("validation-pass-route") -> ValidationPassService -> direct:send-pass-email
 * 4. Validation Fail Route ("validation-fail-route") -> ValidationFailService -> direct:send-fail-email
 * 5. Email Pass Route ("send-pass-email-route") -> EmailNotificationService (PASS)
 * 6. Email Fail Route ("send-fail-email-route") -> EmailNotificationService (FAIL)
 */
@Component
@ConditionalOnProperty(
        name = "validation.sqs.route.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ValidationRoute extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ValidationRoute.class);

    private final ValidationProcessor validationProcessor;
    private final ValidationPassService validationPassService;
    private final ValidationFailService validationFailService;
    private final EmailNotificationService emailNotificationService;

    public ValidationRoute(ValidationProcessor validationProcessor,
                           ValidationPassService validationPassService,
                           ValidationFailService validationFailService,
                           EmailNotificationService emailNotificationService) {
        this.validationProcessor = validationProcessor;
        this.validationPassService = validationPassService;
        this.validationFailService = validationFailService;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public void configure() {

        // Global Exception Handler
        onException(Exception.class)
            .handled(false)
            .logStackTrace(true)
            .logExhausted(true)
            .log("❌ Exception caught in validation workflow: ${exception.message}");

        // =========================================================================
        // 1. Main SQS Ingestion Route
        // =========================================================================
        from("aws2-sqs:manuscript-validation-queue"
                + "?region=ap-south-1"
                + "&autoCreateQueue=false"
                + "&useDefaultCredentialsProvider=true"
                + "&maxMessagesPerPoll=1"
                + "&visibilityTimeout=300"
                + "&waitTimeSeconds=20")
            .routeId("validation-service-route")
            .log("📨 Received incoming manuscript message from SQS: ${header.JMSMessageID}")
            .to("direct:validate-manuscript");

        // =========================================================================
        // 2. Validation Execution & Branching Route
        // =========================================================================
        from("direct:validate-manuscript")
            .routeId("validate-manuscript-route")
            .process(validationProcessor)
            .choice()
                .when(header("VALIDATION_PASSED").isEqualTo(true))
                    .log("✅ Validation SUCCEEDED for request: ${header.REQUEST_ID}")
                    .to("direct:validation-pass")
                .otherwise()
                    .log("🚫 Validation REJECTED for request: ${header.REQUEST_ID}")
                    .to("direct:validation-fail")
            .end()
            .log("✨ Validation workflow completed for request: ${header.REQUEST_ID}");

        // =========================================================================
        // 3. Validation Pass Route (MongoDB Activity, S3 Archive, SNS Publish)
        // =========================================================================
        from("direct:validation-pass")
            .routeId("validation-pass-route")
            .process(exchange -> {
                String requestId = exchange.getIn().getHeader("REQUEST_ID", String.class);
                FileUploadedEvent event = exchange.getProperty("EVENT_OBJECT", FileUploadedEvent.class);
                String fileName = exchange.getProperty("FILE_NAME", String.class);
                byte[] content = exchange.getProperty("FILE_CONTENT", byte[].class);
                ValidationResultDto result = exchange.getProperty("VALIDATION_RESULT", ValidationResultDto.class);

                validationPassService.handleValidationPass(requestId, event, fileName, content, result);
            })
            .to("direct:send-pass-email");

        // =========================================================================
        // 4. Validation Fail Route (MongoDB Activity, SNS Failure Event, No S3 Archive)
        // =========================================================================
        from("direct:validation-fail")
            .routeId("validation-fail-route")
            .process(exchange -> {
                String requestId = exchange.getIn().getHeader("REQUEST_ID", String.class);
                FileUploadedEvent event = exchange.getProperty("EVENT_OBJECT", FileUploadedEvent.class);
                ValidationResultDto result = exchange.getProperty("VALIDATION_RESULT", ValidationResultDto.class);

                validationFailService.handleValidationFail(requestId, event, result);
            })
            .to("direct:send-fail-email");

        // =========================================================================
        // 5. Email Pass Route
        // =========================================================================
        from("direct:send-pass-email")
            .routeId("send-pass-email-route")
            .process(exchange -> {
                String requestId = exchange.getIn().getHeader("REQUEST_ID", String.class);
                FileUploadedEvent event = exchange.getProperty("EVENT_OBJECT", FileUploadedEvent.class);
                emailNotificationService.sendPassNotification(requestId, event);
            });

        // =========================================================================
        // 6. Email Fail Route
        // =========================================================================
        from("direct:send-fail-email")
            .routeId("send-fail-email-route")
            .process(exchange -> {
                String requestId = exchange.getIn().getHeader("REQUEST_ID", String.class);
                FileUploadedEvent event = exchange.getProperty("EVENT_OBJECT", FileUploadedEvent.class);
                ValidationResultDto result = exchange.getProperty("VALIDATION_RESULT", ValidationResultDto.class);
                String failedStep = exchange.getProperty("FAILED_VALIDATION_NAME", String.class);

                emailNotificationService.sendFailNotification(requestId, event, result, failedStep);
            });
    }
}
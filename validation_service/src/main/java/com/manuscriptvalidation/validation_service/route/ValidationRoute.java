package com.manuscriptvalidation.validation_service.route;

import com.manuscriptvalidation.validation_service.processor.ValidationProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ValidationRoute - Orchestrates the manuscript validation workflow via Apache Camel
 * 
 * Responsibilities:
 * - Consumes messages from SQS validation queue
 * - Routes messages to ValidationProcessor
 * - Logs validation results
 * - Handles exceptions gracefully
 * 
 * Flow:
 * SQS Message → Parse → ValidationProcessor (handles all validation logic) → Log Result → Complete
 * 
 * Note: ValidationProcessor handles ALL the work:
 * - Validates metadata
 * - Archives validated documents to S3
 * - Sends email notifications
 * - Records activities to database
 * - Publishes results to SNS
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

    public ValidationRoute(ValidationProcessor validationProcessor) {
        this.validationProcessor = validationProcessor;
    }

    @Override
    public void configure() {

        // Global exception handler
        onException(Exception.class)
            .handled(false)
            .logStackTrace(true)
            .logExhausted(true)
            .log("❌ Exception in validation route: ${exception.message}");

        // Main validation route
        from("aws2-sqs:manuscript-validation-queue"
                + "?region=ap-south-1"
                + "&autoCreateQueue=false"
                + "&useDefaultCredentialsProvider=true"
                + "&maxMessagesPerPoll=1"
                + "&visibilityTimeout=300"
                + "&waitTimeSeconds=20")
            .routeId("validation-service-route")
            
            // Log incoming message
            .log("📨 Received message from validation queue: ${header.JMSMessageID}")
            
            // Process the validation (all logic happens here)
            .process(validationProcessor)
            
            // Route based on validation result
            .choice()
                .when(header("VALIDATION_PASSED").isEqualTo(true))
                    .log("✅ VALIDATION PASSED for request: ${header.REQUEST_ID}")
                .when(header("VALIDATION_PASSED").isEqualTo(false))
                    .log("❌ VALIDATION FAILED for request: ${header.REQUEST_ID}")
                .otherwise()
                    .log("⚠️  VALIDATION STATUS UNKNOWN for request: ${header.REQUEST_ID}")
            .end()
            
            // Final log
            .log("✨ Validation workflow completed for request: ${header.REQUEST_ID}");
    }
}
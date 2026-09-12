package com.manuscriptvalidation.validation_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Destination;

@Service
public class SesEmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(SesEmailService.class);
    
    private final SesClient sesClient;
    
    @Value("${aws.ses.from-email}")
    private String fromEmail;
    
    public SesEmailService(SesClient sesClient) {
        this.sesClient = sesClient;
    }
    
    /**
     * Send email using AWS SES
     * @param toEmail recipient email address
     * @param subject email subject
     * @param body email body text
     * @return true if email sent successfully, false if email sending failed
     */
    public boolean sendEmail(String toEmail, String subject, String body) {
        try {
            logger.info("📧 Sending email to: {}", toEmail);
            
            Destination destination = Destination.builder()
                .toAddresses(toEmail)
                .build();
            
            Content subjectContent = Content.builder()
                .data(subject)
                .charset("UTF-8")
                .build();
            
            Content bodyContent = Content.builder()
                .data(body)
                .charset("UTF-8")
                .build();
            
            Body emailBody = Body.builder()
                .text(bodyContent)
                .build();
            
            SendEmailRequest sendEmailRequest = SendEmailRequest.builder()
                .source(fromEmail)
                .destination(destination)
                .message(software.amazon.awssdk.services.ses.model.Message.builder()
                    .subject(subjectContent)
                    .body(emailBody)
                    .build())
                .build();
            
            SendEmailResponse result = sesClient.sendEmail(sendEmailRequest);
            
            logger.info("✅ Email sent successfully. Message ID: {}", result.messageId());
            return true;
            
        } catch (Exception e) {
            logger.error("❌ Failed to send email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
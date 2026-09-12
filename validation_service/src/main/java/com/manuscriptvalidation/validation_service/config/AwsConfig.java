package com.manuscriptvalidation.validation_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class AwsConfig {
    
    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
            .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
            .build();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
            .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
            .build();
    }
    
    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
            .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
            .build();
    }
}
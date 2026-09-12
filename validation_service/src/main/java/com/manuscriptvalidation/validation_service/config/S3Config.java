package com.manuscriptvalidation.validation_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client() {

        log.info("Initializing S3Client with default AWS credentials and region: ap-south-1");

        return S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .build();
    }
}
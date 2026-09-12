package com.manuscriptvalidation.validation_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "validation.sqs.route.enabled=false"
})
class ValidationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
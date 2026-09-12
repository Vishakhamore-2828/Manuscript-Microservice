package com.manuscriptvalidation.validation_service.repository;

import com.manuscriptvalidation.validation_service.dto.ValidationStatusDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ValidationStatusRepository
        extends MongoRepository<ValidationStatusDocument, String> {

    Optional<ValidationStatusDocument> findByRequestId(String requestId);
}
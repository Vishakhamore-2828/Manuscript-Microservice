package com.manuscriptvalidation.validation_service.repository;

import com.manuscriptvalidation.validation_service.dto.BookEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BookEventRepository
        extends MongoRepository<BookEventDocument, String> {

    Optional<BookEventDocument> findByS3Reference(String s3Reference);
}
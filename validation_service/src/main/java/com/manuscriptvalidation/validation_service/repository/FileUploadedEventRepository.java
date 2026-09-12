package com.manuscriptvalidation.validation_service.repository;

import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public interface FileUploadedEventRepository 
        extends MongoRepository<FileUploadedEvent, String> {

    /**
     * Find request by requestId (Validation Service needs this)
     */
    Optional<FileUploadedEvent> findByRequestId(String requestId);
    
    /**
     * Find request by S3 reference (for cross-checking)
     */
    Optional<FileUploadedEvent> findByS3Reference(String s3Reference);
    
    /**
     * Append activity to activities array
     * Used by Validation Service to track: VALIDATION_STARTED, VALIDATION_PASSED, etc.
     */
    @Query("{ 'requestId': ?0 }")
    @Update("{ '$push': { 'activities': ?1 } }")
    void pushActivity(String requestId, Map<String, Object> activity);
}
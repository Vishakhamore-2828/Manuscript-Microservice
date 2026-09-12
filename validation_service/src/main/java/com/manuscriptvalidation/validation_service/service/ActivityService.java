package com.manuscriptvalidation.validation_service.service;

import com.manuscriptvalidation.validation_service.dto.Activity;
import com.manuscriptvalidation.validation_service.enums.ActivityType;
import com.manuscriptvalidation.validation_service.repository.FileUploadedEventRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class ActivityService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityService.class);
    
    private final FileUploadedEventRepository eventRepository;
    
    public ActivityService(FileUploadedEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }
    
    /**
     * Simple: Append activity to MongoDB activities array
     * 
     * Example: addActivity("REQ-0019", ActivityType.VALIDATION_STARTED)
     * This adds one activity record to the activities list
     */
    public void addActivity(String requestId, ActivityType activityType) {
        try {
            // Convert to Map for MongoDB $push
            Map<String, Object> activityMap = new HashMap<>();
            activityMap.put("activityType", activityType.toString());
            activityMap.put("applicationName", "validation-service");
            activityMap.put("timestamp", LocalDateTime.now());
            
            // Push to activities array in MongoDB
            eventRepository.pushActivity(requestId, activityMap);
            
            logger.info("✅ Activity saved: requestId={}, type={}", requestId, activityType);
            
        } catch (Exception e) {
            logger.error("❌ Failed to add activity: requestId={}, error={}", requestId, e.getMessage(), e);
        }
    }
}
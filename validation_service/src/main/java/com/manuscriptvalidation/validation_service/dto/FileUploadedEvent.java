package com.manuscriptvalidation.validation_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "book_processing_requests")
public class FileUploadedEvent {

    private String eventType;
    private String requestId;
    private String bookId;  
    private String bookName;
    private String authorId;
    private String authorEmail;
    private String fileFormat;
    private String isbn;
    private String s3Reference;
    

    // ← Activities list for tracking validation progress
    private List<Activity> activities;
}

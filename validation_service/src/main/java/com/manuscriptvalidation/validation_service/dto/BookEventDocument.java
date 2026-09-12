package com.manuscriptvalidation.validation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "upload")
public class BookEventDocument {

    private String requestId;
    private String authorId;
    private String bookName;
    @JsonProperty("ISBN")
    private String isbn;
    private String eventType;
    private List<Activity> activities;
    private String s3Reference;
}
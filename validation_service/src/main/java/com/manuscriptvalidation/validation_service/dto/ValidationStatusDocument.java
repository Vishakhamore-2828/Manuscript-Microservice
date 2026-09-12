package com.manuscriptvalidation.validation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Validation_Status")
public class ValidationStatusDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String requestId;

    private ValidationStatus isbn;

    private ValidationStatus fileExistence;

    private ValidationStatus fileExtension;

    private ValidationStatus fileSize;

    private ValidationStatus fileCorruption;

    private ValidationStatus fileName;

    private ValidationStatus requiredMetadata;
}
package com.manuscriptvalidation.validation_service.dto;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    private String state;
    private String applicationName;
    private String timestamp;
    private String status;

    private List<ValidationErrorDto> validationErrors;
}
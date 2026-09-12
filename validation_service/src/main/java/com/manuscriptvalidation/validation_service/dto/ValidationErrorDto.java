package com.manuscriptvalidation.validation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@AllArgsConstructor 
@NoArgsConstructor 
@Getter 
@Setter 
public class ValidationErrorDto {

    private String code;
    private String message;
}
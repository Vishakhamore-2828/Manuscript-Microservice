package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;

import org.springframework.stereotype.Component;

@Component
public class RequiredMetadataValidator {

    public ValidationResultDto validate(FileUploadedEvent event) {

        ValidationResultDto result = new ValidationResultDto();

        if (event == null) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_METADATA",
                            "Event metadata is missing"
                    )
            );
            result.setPassed(false);
            return result;
        }

        // ✅ REQUIRED FIELDS (must not be blank)
        if (isBlank(event.getRequestId())) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_REQUEST_ID",
                            "Request ID is missing"
                    )
            );
        }

        if (isBlank(event.getBookId())) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_BOOK_ID",
                            "Book ID is missing"
                    )
            );
        }

        if (isBlank(event.getBookName())) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_BOOK_NAME",
                            "Book name is missing"
                    )
            );
        }

        if (isBlank(event.getAuthorId())) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_AUTHOR_ID",
                            "Author ID is missing"
                    )
            );
        }

        if (isBlank(event.getAuthorEmail())) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_AUTHOR_EMAIL",
                            "Author email is missing"
                    )
            );
        }

        if (isBlank(event.getS3Reference())) {
            result.addError(
                    new ValidationErrorDto(
                            "MISSING_S3_REFERENCE",
                            "S3 reference is missing"
                    )
            );
        }

        // ✅ OPTIONAL FIELDS (can be blank, but validate if present)
        if (!isBlank(event.getIsbn()) && !isValidISBN(event.getIsbn())) {
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_ISBN",
                            "ISBN format is invalid"
                    )
            );
        }

        result.setPassed(result.getErrors().isEmpty());
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ✅ Comprehensive ISBN validation with checksum verification
    private boolean isValidISBN(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return true; // Optional field
        }
        
        // Remove hyphens, spaces, and other formatting characters
        String cleaned = isbn.replaceAll("[^0-9X]", "").toUpperCase();
        
        // Check if it's ISBN-10 or ISBN-13
        if (cleaned.length() == 10) {
            return isValidISBN10(cleaned);
        } else if (cleaned.length() == 13) {
            return isValidISBN13(cleaned);
        } else {
            // Invalid length - must be 10 or 13 digits
            return false;
        }
    }

    /**
     * Validate ISBN-10 with checksum
     * Formula: (10×digit₁ + 9×digit₂ + ... + 1×digit₁₀) mod 11 = 0
     * Last digit can be 'X' (represents 10)
     */
    private boolean isValidISBN10(String isbn10) {
        // ISBN-10 must be 10 characters
        if (isbn10.length() != 10) {
            return false;
        }
        
        // Validate format: first 9 must be digits, 10th can be digit or X
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(isbn10.charAt(i))) {
                return false; // First 9 chars must be digits
            }
        }
        
        char lastChar = isbn10.charAt(9);
        if (!Character.isDigit(lastChar) && lastChar != 'X') {
            return false; // Last char must be digit or X
        }
        
        // Calculate checksum
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (isbn10.charAt(i) - '0') * (10 - i);
        }
        
        int checkDigit = lastChar == 'X' ? 10 : (lastChar - '0');
        sum += checkDigit;
        
        return sum % 11 == 0;
    }

    /**
     * Validate ISBN-13 with checksum
     * Formula: Sum of (digit × weight) where weight alternates 1,3,1,3,...
     * Result mod 10 should equal 0
     */
    private boolean isValidISBN13(String isbn13) {
        // ISBN-13 must be 13 digits
        if (isbn13.length() != 13) {
            return false;
        }
        
        // All characters must be digits
        for (char c : isbn13.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        
        // Calculate checksum
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = isbn13.charAt(i) - '0';
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += digit * weight;
        }
        
        int checkDigit = isbn13.charAt(12) - '0';
        int calculatedCheck = (10 - (sum % 10)) % 10;
        
        return calculatedCheck == checkDigit;
    }
}
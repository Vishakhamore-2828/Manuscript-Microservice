package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.springframework.stereotype.Component;

/**
 * Validates ISBN format and checksum
 * Supports both ISBN-10 and ISBN-13 with comprehensive checksum validation
 * ISBN is optional - returns true if blank
 */
@Component
public class IsbnValidator {

    /**
     * Validate ISBN string (optional field)
     * Returns true if ISBN is blank (optional)
     * Returns true if ISBN-10 or ISBN-13 with valid checksum
     * Returns false if ISBN is invalid
     */
    public ValidationResultDto validate(String isbn) {
        ValidationResultDto result = new ValidationResultDto();

        if (isbn == null || isbn.isBlank()) {
            result.setPassed(true); // ISBN is optional
            return result;
        }

        if (!isValidISBN(isbn)) {
            result.addError(
                    new ValidationErrorDto(
                            "INVALID_ISBN",
                            "ISBN format is invalid or checksum verification failed"
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Comprehensive ISBN validation with checksum verification
     */
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
            return false; // Invalid length
        }
    }

    /**
     * Validate ISBN-10 with checksum
     * Formula: (10×digit₁ + 9×digit₂ + ... + 1×digit₁₀) mod 11 = 0
     * Last digit can be 'X' (represents 10)
     */
    private boolean isValidISBN10(String isbn10) {
        if (isbn10.length() != 10) {
            return false;
        }
        
        // First 9 must be digits
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(isbn10.charAt(i))) {
                return false;
            }
        }
        
        // 10th can be digit or X
        char lastChar = isbn10.charAt(9);
        if (!Character.isDigit(lastChar) && lastChar != 'X') {
            return false;
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
     * Formula: Σ(digit × weight) mod 10 = 0
     * Weights alternate: 1, 3, 1, 3, ...
     */
    private boolean isValidISBN13(String isbn13) {
        if (isbn13.length() != 13) {
            return false;
        }
        
        // Must be 13 digits
        if (!isbn13.matches("\\d{13}")) {
            return false;
        }
        
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(isbn13.charAt(i));
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += digit * weight;
        }
        
        int checkDigit = Character.getNumericValue(isbn13.charAt(12));
        int calculatedCheckDigit = (10 - (sum % 10)) % 10;
        
        return checkDigit == calculatedCheckDigit;
    }
}
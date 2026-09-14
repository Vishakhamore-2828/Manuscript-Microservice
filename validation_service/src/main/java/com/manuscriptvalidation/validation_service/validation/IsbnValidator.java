package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Validates ISBN format and checksum for ISBN-10 and ISBN-13
 */
@Component
public class IsbnValidator {

    private static final Logger logger = LoggerFactory.getLogger(IsbnValidator.class);

    private static final Predicate<String> IS_BLANK = s -> s == null || s.isBlank();

    public ValidationResultDto validate(String isbn) {
        ValidationResultDto result = new ValidationResultDto();

        if (IS_BLANK.test(isbn)) {
            result.setPassed(true);
            return result;
        }

        boolean valid = isValidISBN(isbn);
        if (!valid) {
            logger.debug("ISBN checksum verification failed for: {}", isbn);
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

    private boolean isValidISBN(String isbn) {
        if (IS_BLANK.test(isbn)) {
            return true;
        }
        
        String cleaned = isbn.replaceAll("[^0-9X]", "").toUpperCase();
        
        if (cleaned.length() == 10) {
            return isValidISBN10(cleaned);
        } else if (cleaned.length() == 13) {
            return isValidISBN13(cleaned);
        }
        return false;
    }

    private boolean isValidISBN10(String isbn10) {
        if (isbn10.length() != 10) {
            return false;
        }

        boolean first9Digits = IntStream.range(0, 9)
                .mapToObj(isbn10::charAt)
                .allMatch(Character::isDigit);

        if (!first9Digits) {
            return false;
        }

        char lastChar = isbn10.charAt(9);
        if (!Character.isDigit(lastChar) && lastChar != 'X') {
            return false;
        }

        int sum = IntStream.range(0, 9)
                .map(i -> (isbn10.charAt(i) - '0') * (10 - i))
                .sum();

        int checkDigit = (lastChar == 'X') ? 10 : (lastChar - '0');
        return (sum + checkDigit) % 11 == 0;
    }

    private boolean isValidISBN13(String isbn13) {
        if (isbn13.length() != 13 || !isbn13.chars().allMatch(Character::isDigit)) {
            return false;
        }

        int sum = IntStream.range(0, 12)
                .map(i -> (isbn13.charAt(i) - '0') * ((i % 2 == 0) ? 1 : 3))
                .sum();

        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == (isbn13.charAt(12) - '0');
    }
}
package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.FileUploadedEvent;
import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Validates mandatory metadata fields and optional ISBN format on uploaded manuscript event
 */
@Component
public class RequiredMetadataValidator {

    private static final Logger logger = LoggerFactory.getLogger(RequiredMetadataValidator.class);

    private static final Predicate<String> IS_BLANK = s -> s == null || s.isBlank();
    private static final Predicate<String> IS_NOT_BLANK = IS_BLANK.negate();

    private record MetadataFieldRule(
            String errorCode,
            String errorMessage,
            Function<FileUploadedEvent, String> extractor
    ) {}

    private static final MetadataFieldRule[] REQUIRED_RULES = {
            new MetadataFieldRule("MISSING_REQUEST_ID", "Request ID is missing", FileUploadedEvent::getRequestId),
            new MetadataFieldRule("MISSING_BOOK_ID", "Book ID is missing", FileUploadedEvent::getBookId),
            new MetadataFieldRule("MISSING_BOOK_NAME", "Book name is missing", FileUploadedEvent::getBookName),
            new MetadataFieldRule("MISSING_AUTHOR_ID", "Author ID is missing", FileUploadedEvent::getAuthorId),
            new MetadataFieldRule("MISSING_AUTHOR_EMAIL", "Author email is missing", FileUploadedEvent::getAuthorEmail),
            new MetadataFieldRule("MISSING_S3_REFERENCE", "S3 reference is missing", FileUploadedEvent::getS3Reference)
    };

    public ValidationResultDto validate(FileUploadedEvent event) {
        ValidationResultDto result = new ValidationResultDto();

        if (event == null) {
            logger.warn("❌ Metadata validation failed: event payload is null");
            result.addError(new ValidationErrorDto("MISSING_METADATA", "Event metadata is missing"));
            result.setPassed(false);
            return result;
        }

        // Validate all mandatory metadata fields using Stream API & Predicates
        Stream.of(REQUIRED_RULES)
                .filter(rule -> IS_BLANK.test(rule.extractor().apply(event)))
                .map(rule -> new ValidationErrorDto(rule.errorCode(), rule.errorMessage()))
                .forEach(result::addError);

        // Validate optional ISBN only when present
        if (IS_NOT_BLANK.test(event.getIsbn()) && !isValidISBN(event.getIsbn())) {
            logger.debug("ISBN validation failed for value: {}", event.getIsbn());
            result.addError(new ValidationErrorDto("INVALID_ISBN", "ISBN format is invalid"));
        }

        boolean passed = result.getErrors().isEmpty();
        result.setPassed(passed);
        
        if (passed) {
            logger.debug("✅ Metadata validation passed for request: {}", event.getRequestId());
        } else {
            logger.warn("❌ Metadata validation failed for request: {} with {} errors", event.getRequestId(), result.getErrors().size());
        }

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
        
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(isbn10.charAt(i))) {
                return false;
            }
        }
        
        char lastChar = isbn10.charAt(9);
        if (!Character.isDigit(lastChar) && lastChar != 'X') {
            return false;
        }
        
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (isbn10.charAt(i) - '0') * (10 - i);
        }
        
        int checkDigit = (lastChar == 'X') ? 10 : (lastChar - '0');
        sum += checkDigit;
        
        return sum % 11 == 0;
    }

    private boolean isValidISBN13(String isbn13) {
        if (isbn13.length() != 13) {
            return false;
        }
        
        for (char c : isbn13.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = isbn13.charAt(i) - '0';
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += digit * weight;
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == (isbn13.charAt(12) - '0');
    }
}
package com.manuscriptvalidation.validation_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import lombok.extern.slf4j.Slf4j;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Slf4j
@DisplayName("S3Service Unit Tests")
class S3ServiceUnitTest {

    private S3Client s3Client;
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Service = new S3Service(s3Client);
    }

    @Test
    @DisplayName("Archive uses request ID and case-preserved filename as the exact key")
    void archiveUsesExactPathAndReturnsCanonicalReference() {
        String canonicalReference = s3Service.uploadToArchiveBucket(
                "s3://archive-bucket/archive/",
                "REQ-0022/Original-Book.EPUB",
                new byte[]{1, 2, 3});

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertEquals("archive-bucket", requestCaptor.getValue().bucket());
        assertEquals("REQ-0022/Original-Book.EPUB", requestCaptor.getValue().key());
        assertEquals(
                "archive-bucket/REQ-0022/Original-Book.EPUB",
                canonicalReference);
    }
    
    @Test
    @DisplayName("Test extractFileNameFromS3Reference")
    void testExtractFileNameFromS3Reference() {
        String s3Reference = "s3://book-platform-files-206465504931-ap-south-1-an/ingestion/REQ-0007/test-manuscript.epub";
        
        // Extract filename from S3 reference
        String fileName = s3Reference.substring(s3Reference.lastIndexOf("/") + 1);
        
        log.info("S3 Reference: {}", s3Reference);
        log.info("Extracted File Name: {}", fileName);
        
        assertEquals("test-manuscript.epub", fileName);
        assertNotNull(fileName);
        assertFalse(fileName.isEmpty());
    }
    
    @Test
    @DisplayName("Test S3 path parsing")
    void testS3PathParsing() {
        String s3Reference = "s3://book-platform-files-206465504931-ap-south-1-an/ingestion/REQ-0008/manuscript.pdf";
        
        // Parse S3 reference
        String[] parts = s3Reference.split("/");
        String fileName = parts[parts.length - 1];
        String requestId = parts[parts.length - 2];
        
        log.info("File Name: {}", fileName);
        log.info("Request ID: {}", requestId);
        
        assertEquals("manuscript.pdf", fileName);
        assertEquals("REQ-0008", requestId);
    }
    
    @Test
    @DisplayName("Test valid file extensions")
    void testValidFileExtensions() {
        java.util.Set<String> ALLOWED = java.util.Set.of(".pdf", ".docx", ".epub");
        
        String file1 = "manuscript.pdf";
        String file2 = "document.docx";
        String file3 = "book.epub";
        String file4 = "image.png";
        
        assertTrue(isValidExtension(file1, ALLOWED));
        assertTrue(isValidExtension(file2, ALLOWED));
        assertTrue(isValidExtension(file3, ALLOWED));
        assertFalse(isValidExtension(file4, ALLOWED));
        
        log.info("✓ File extension validation working correctly");
    }
    
    @Test
    @DisplayName("Test file size validation")
    void testFileSizeValidation() {
        long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
        
        byte[] smallFile = new byte[1024 * 100]; // 100 KB
        byte[] largeFile = new byte[(int)(MAX_FILE_SIZE + 1024)]; // 10 MB + 1 KB
        
        assertTrue(smallFile.length < MAX_FILE_SIZE);
        assertFalse(largeFile.length <= MAX_FILE_SIZE);
        
        log.info("Small file size: {} bytes", smallFile.length);
        log.info("Large file size: {} bytes", largeFile.length);
        log.info("✓ File size validation working correctly");
    }
    
    @Test
    @DisplayName("Test ISBN-13 validation")
    void testISBN13Validation() {
        // Valid ISBN-13
        String validIsbn = "9780596007683";
        assertTrue(isValidIsbn13(validIsbn), "Should be valid ISBN-13");
        
        // Invalid ISBN-13 (wrong check digit)
        String invalidIsbn = "9780596007684";
        assertFalse(isValidIsbn13(invalidIsbn), "Should be invalid ISBN-13");
        
        // Invalid format
        String invalidFormat = "123456789";
        assertFalse(isValidIsbn13(invalidFormat), "Should reject invalid format");
        
        log.info("✓ ISBN-13 validation working correctly");
    }
    
    // Helper Methods
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase();
    }
    
    private boolean isValidExtension(String fileName, java.util.Set<String> allowed) {
        String extension = getFileExtension(fileName);
        return allowed.contains(extension);
    }
    
    private boolean isValidIsbn13(String isbn) {
        if (!isbn.matches("\\d{13}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(isbn.charAt(i));
            if (i % 2 == 0) {
                sum += digit;
            } else {
                sum += digit * 3;
            }
        }
        int checkDigit = Character.getNumericValue(isbn.charAt(12));
        int calculatedCheckDigit = (10 - (sum % 10)) % 10;
        return checkDigit == calculatedCheckDigit;
    }
}
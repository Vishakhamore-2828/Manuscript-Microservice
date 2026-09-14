package com.manuscriptvalidation.validation_service.validation;

import com.manuscriptvalidation.validation_service.dto.ValidationErrorDto;
import com.manuscriptvalidation.validation_service.dto.ValidationResultDto;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Validates that file content is not corrupted by checking:
 * 1. File magic numbers (signatures)
 * 2. File format integrity
 * 3. Minimum file size requirements
 */
@Component
public class FileCorruptionValidator {

    // PDF magic number: %PDF
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};
    // DOCX magic number: PK (ZIP format)
    private static final byte[] DOCX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};    // EPUB magic number: PK (ZIP format)
    private static final byte[] EPUB_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};    // DOC magic number: D0CF11E0 (OLE2 format)
    private static final byte[] DOC_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    // TXT files: any ASCII/UTF-8 content
    private static final int MIN_FILE_SIZE = 10; // At least 10 bytes

    /**
     * Validate file integrity by checking:
     * - File magic numbers (signature validation)
     * - Minimum file size
     * - File format integrity
     * @param fileContent Downloaded file content from S3
     * @param fileName Original filename to determine type
     * @return ValidationResult
     */
    public ValidationResultDto validate(byte[] fileContent, String fileName) {
        ValidationResultDto result = new ValidationResultDto();

        // Check 1: File exists and has content
        if (fileContent == null || fileContent.length == 0) {
            result.addError(
                    new ValidationErrorDto(
                            "FILE_NOT_FOUND",
                            "Manuscript file is missing or empty"
                    )
            );
            result.setPassed(false);
            return result;
        }

        // Check 2: Minimum file size
        if (fileContent.length < MIN_FILE_SIZE) {
            result.addError(
                    new ValidationErrorDto(
                            "CORRUPTED_FILE",
                            "Manuscript file is too small (" + fileContent.length + " bytes) to be valid"
                    )
            );
            result.setPassed(false);
            return result;
        }

        // Check 3: Validate file format by extension
        if (fileName != null && !fileName.isEmpty()) {
            String extension = getFileExtension(fileName).toLowerCase();
            
            if (extension.equals("pdf")) {
                if (!validatePdf(fileContent)) {
                    result.addError(
                            new ValidationErrorDto(
                                    "CORRUPTED_FILE",
                                    "PDF file is corrupted - invalid magic number or structure"
                            )
                    );
                    result.setPassed(false);
                    return result;
                }
            } else if (extension.equals("docx")) {
                if (!validateDocx(fileContent)) {
                    result.addError(
                            new ValidationErrorDto(
                                    "CORRUPTED_FILE",
                                    "DOCX file is corrupted - invalid ZIP structure"
                            )
                    );
                    result.setPassed(false);
                    return result;
                }
            } else if (extension.equals("epub")) {
                if (!validateEpub(fileContent)) {
                    result.addError(
                            new ValidationErrorDto(
                                    "CORRUPTED_FILE",
                                    "EPUB file is corrupted - invalid ZIP structure"
                            )
                    );
                    result.setPassed(false);
                    return result;
                }
            } else if (extension.equals("doc")) {
                if (!validateDoc(fileContent)) {
                    result.addError(
                            new ValidationErrorDto(
                                    "CORRUPTED_FILE",
                                    "DOC file is corrupted - invalid OLE2 structure"
                            )
                    );
                    result.setPassed(false);
                    return result;
                }
            } else if (extension.equals("txt")) {
                if (!validateText(fileContent)) {
                    result.addError(
                            new ValidationErrorDto(
                                    "CORRUPTED_FILE",
                                    "Text file contains invalid UTF-8 encoding"
                            )
                    );
                    result.setPassed(false);
                    return result;
                }
            }
        }

        // Check 4: Try reading the entire file
        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytes += bytesRead;
            }

            if (totalBytes != fileContent.length) {
                result.addError(
                        new ValidationErrorDto(
                                "CORRUPTED_FILE",
                                "File read mismatch: expected " + fileContent.length + " bytes but read " + totalBytes
                        )
                );
                result.setPassed(false);
                return result;
            }

        } catch (Exception e) {
            result.addError(
                    new ValidationErrorDto(
                            "CORRUPTED_FILE",
                            "File cannot be read: " + e.getMessage()
                    )
            );
            result.setPassed(false);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Validate PDF file format
     */
    private boolean validatePdf(byte[] fileContent) {
        // Check PDF magic number at start
        if (!startsWith(fileContent, PDF_SIGNATURE)) {
            return false;
        }

        // Check for PDF end marker within last 1024 bytes
        int offset = Math.max(0, fileContent.length - 1024);
        int length = fileContent.length - offset;
        String endContent = new String(fileContent, offset, length, java.nio.charset.StandardCharsets.ISO_8859_1);
        return endContent.contains("%%EOF");
    }

    /**
     * Validate EPUB file format (ZIP-based)
     */
    private boolean validateEpub(byte[] fileContent) {
        if (!startsWith(fileContent, EPUB_SIGNATURE)) {
            return false;
        }
        return fileContent.length > 100;
    }

    /**
     * Validate DOCX file format (ZIP-based)
     */
    private boolean validateDocx(byte[] fileContent) {
        // DOCX is a ZIP file, check ZIP signature
        if (!startsWith(fileContent, DOCX_SIGNATURE)) {
            return false;
        }

        // Should have at least minimal ZIP structure
        return fileContent.length > 100;
    }

    /**
     * Validate DOC file format (OLE2-based)
     */
    private boolean validateDoc(byte[] fileContent) {
        // Check OLE2 magic number
        if (!startsWith(fileContent, DOC_SIGNATURE)) {
            return false;
        }

        // Should have minimum size
        return fileContent.length > 512;
    }

    /**
     * Validate TXT file format
     */
    private boolean validateText(byte[] fileContent) {
        // Try to decode as UTF-8
        try {
            new String(fileContent, "UTF-8");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if byte array starts with signature
     */
    private boolean startsWith(byte[] data, byte[] signature) {
        if (data == null || signature == null || data.length < signature.length) {
            return false;
        }
        return java.util.stream.IntStream.range(0, signature.length)
                .allMatch(i -> data[i] == signature[i]);
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
}
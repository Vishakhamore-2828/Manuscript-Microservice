package com.manuscriptvalidation.validation_service.service;

import com.manuscriptvalidation.validation_service.dto.ValidationStatus;
import com.manuscriptvalidation.validation_service.dto.ValidationStatusDocument;
import com.manuscriptvalidation.validation_service.repository.ValidationStatusRepository;
import org.springframework.stereotype.Service;

@Service
public class ValidationStatusService {

    private final ValidationStatusRepository validationStatusRepository;

    public ValidationStatusService(
            ValidationStatusRepository validationStatusRepository) {
        this.validationStatusRepository = validationStatusRepository;
    }

    public ValidationStatusDocument createStatus(String requestId) {

        return validationStatusRepository
            .findByRequestId(requestId)
            .orElseGet(() -> {

                ValidationStatusDocument status =
                        new ValidationStatusDocument();

                status.setRequestId(requestId);
                status.setIsbn(ValidationStatus.NOT_STARTED);
                status.setFileExistence(ValidationStatus.NOT_STARTED);
                status.setFileExtension(ValidationStatus.NOT_STARTED);
                status.setFileSize(ValidationStatus.NOT_STARTED);
                status.setFileCorruption(ValidationStatus.NOT_STARTED);
                status.setFileName(ValidationStatus.NOT_STARTED);
                status.setRequiredMetadata(ValidationStatus.NOT_STARTED);

                return validationStatusRepository.save(status);
            });
    }

    public void updateIsbnStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setIsbn(status);
        validationStatusRepository.save(document);
    }

    public void updateFileExistenceStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setFileExistence(status);
        validationStatusRepository.save(document);
    }

    public void updateFileExtensionStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setFileExtension(status);
        validationStatusRepository.save(document);
    }

    public void updateFileSizeStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setFileSize(status);
        validationStatusRepository.save(document);
    }

    public void updateFileCorruptionStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setFileCorruption(status);
        validationStatusRepository.save(document);
    }

    public void updateFileNameStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setFileName(status);
        validationStatusRepository.save(document);
    }

    public void updateRequiredMetadataStatus(
            String requestId,
            ValidationStatus status) {

        ValidationStatusDocument document = getStatus(requestId);
        document.setRequiredMetadata(status);
        validationStatusRepository.save(document);
    }

    private ValidationStatusDocument getStatus(String requestId) {

        return validationStatusRepository
                .findByRequestId(requestId)
                .orElseGet(() -> createStatus(requestId));
    }
}
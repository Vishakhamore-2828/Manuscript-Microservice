package com.manuscriptvalidation.validation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ManuscriptFile {
    private String fileName;
    private byte[] content;
    private long size;
    
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }
}
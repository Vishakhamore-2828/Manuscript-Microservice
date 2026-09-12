package com.manuscriptvalidation.validation_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.core.sync.RequestBody;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class S3Service {
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Value("${aws.s3.object-key-prefix}")
    private String objectKeyPrefix;
    
    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }
    
    /**
     * Check if manuscript file exists in S3
     * @param requestId - Request ID
     * @param fileName - Manuscript file name
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String requestId, String fileName) {
        try {
            String s3Key = objectKeyPrefix + "/" + requestId + "/" + fileName;
            
            log.debug("Checking if file exists in S3: s3://{}/{}", bucketName, s3Key);
            
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            s3Client.headObject(headObjectRequest);
            
            log.info("✓ File exists in S3: {}", fileName);
            return true;
            
        } catch (NoSuchKeyException e) {
            log.warn("✗ File not found in S3: {}", fileName);
            return false;
        } catch (Exception e) {
            log.error("Error checking file existence in S3: {}", e.getMessage(), e);
            throw new RuntimeException("S3 check failed for file: " + fileName, e);
        }
    }
    
    /**
     * Download manuscript file from S3
     * @param requestId - Request ID
     * @param fileName - Manuscript file name
     * @return byte array of file content
     */
    public byte[] downloadManuscript(String requestId, String fileName) {
        try {
            String s3Key = objectKeyPrefix + "/" + requestId + "/" + fileName;
            
            log.info("Downloading manuscript from S3: s3://{}/{}", bucketName, s3Key);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            byte[] content = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            
            log.info("✓ Successfully downloaded manuscript: {} ({} bytes)", 
                    fileName, content.length);
            return content;
            
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: {}", fileName);
            throw new RuntimeException("Manuscript file not found: " + fileName, e);
        } catch (Exception e) {
            log.error("Error downloading from S3: {}", e.getMessage(), e);
            throw new RuntimeException("S3 download failed for file: " + fileName, e);
        }
    }
    
    /**
     * Download manuscript file from S3 by full path
     * @param s3Path - Full S3 path (e.g., "injection/REQ-0037/file.epub")
     * @return byte array of file content
     */
    public byte[] downloadManuscriptByPath(String s3Path) {
        try {
            log.info("Downloading manuscript from S3: s3://{}/{}", bucketName, s3Path);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Path)
                    .build();
            
            byte[] content = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            
            String fileName = s3Path.substring(s3Path.lastIndexOf("/") + 1);
            log.info("✓ Successfully downloaded manuscript: {} ({} bytes)", 
                    fileName, content.length);
            return content;
            
        } catch (NoSuchKeyException e) {
            String fileName = s3Path.substring(s3Path.lastIndexOf("/") + 1);
            log.error("File not found in S3: {}", fileName);
            throw new RuntimeException("Manuscript file not found: " + fileName, e);
        } catch (Exception e) {
            log.error("Error downloading from S3: {}", e.getMessage(), e);
            throw new RuntimeException("S3 download failed", e);
        }
    }

    /**
     * Get file size from S3
     * @param requestId - Request ID
     * @param fileName - Manuscript file name
     * @return file size in bytes
     */
    public long getFileSize(String requestId, String fileName) {
        try {
            String s3Key = objectKeyPrefix + "/" + requestId + "/" + fileName;
            
            log.debug("Getting file size from S3: {}", s3Key);
            
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            long fileSize = s3Client.headObject(headObjectRequest).contentLength();
            
            log.info("File size: {} bytes", fileSize);
            return fileSize;
            
        } catch (Exception e) {
            log.error("Error getting file size from S3: {}", e.getMessage(), e);
            throw new RuntimeException("S3 file size check failed", e);
        }
    }
    
    /**
     * Upload validation result to S3
     * @param requestId - Request ID
     * @param resultFileName - Result file name
     * @param content - JSON content of validation result
     * @return S3 URL of uploaded file
     */
    public String uploadValidationResult(String requestId, String resultFileName, String content) {
        try {
            String s3Key = "validation-results/" + requestId + "/" + resultFileName;
            
            log.info("Uploading validation result to s3://{}/{}", bucketName, s3Key);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("application/json")
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(content));
            
            String s3Url = "s3://" + bucketName + "/" + s3Key;
            log.info("✓ Successfully uploaded validation result: {}", s3Url);
            
            return s3Url;
            
        } catch (Exception e) {
            log.error("Error uploading to S3: {}", e.getMessage(), e);
            throw new RuntimeException("S3 upload failed", e);
        }
    }
    
    /**
     * Upload validated manuscript to archive S3 bucket
     * FIXED: Now adds "archive/" prefix to prevent double paths
     * 
     * @param archiveBucketName - Archive bucket name
     * @param archivePath - Path within archive bucket (e.g., "REQ-0037/fileName")
     * @param content - File content as byte array
     * @return S3 URL of archived file
     */
    public String uploadToArchiveBucket(String archiveBucketName, String archivePath, byte[] content) {
        try {
            // ===== DEBUG #1: Input Validation =====
            log.info("🔵 [DEBUG #1] uploadToArchiveBucket() called");
            log.info("   archiveBucketName (RAW): '{}'", archiveBucketName);
            log.info("   archivePath (RAW): '{}'", archivePath);
            log.info("   content.length: {} bytes", content != null ? content.length : "NULL");
            
            // Check for null values
            if (archiveBucketName == null || archiveBucketName.isBlank()) {
                log.error("❌ [ERROR] archiveBucketName is NULL or BLANK!");
                throw new IllegalArgumentException("archiveBucketName cannot be null or blank");
            }
            
            if (archivePath == null || archivePath.isBlank()) {
                log.error("❌ [ERROR] archivePath is NULL or BLANK!");
                throw new IllegalArgumentException("archivePath cannot be null or blank");
            }
            
            if (content == null || content.length == 0) {
                log.error("❌ [ERROR] content is NULL or EMPTY!");
                throw new IllegalArgumentException("content cannot be null or empty");
            }
            
            // ===== DEBUG #2: Bucket Name Cleaning =====
            // Extract ONLY the bucket name: remove s3:// prefix and everything after first /
            String cleanBucketName = archiveBucketName
                    .replaceAll("^s3://", "")      // Remove s3:// prefix
                    .split("/")[0];                // Extract bucket name only (part before first /)
            log.info("🔵 [DEBUG #2] Bucket Name Cleaning");
            log.info("   cleanBucketName: '{}'", cleanBucketName);
            log.info("   Length: {}", cleanBucketName.length());
            log.info("   Contains spaces: {}", cleanBucketName.contains(" "));
            log.info("   Contains special chars: {}", !cleanBucketName.matches("[a-z0-9-_.]*"));
            
            // ===== DEBUG #3: Archive Path Processing =====
            String fullKey = "archive/" + archivePath;
            log.info("🔵 [DEBUG #3] Full Key Construction");
            log.info("   archivePath: '{}'", archivePath);
            log.info("   fullKey: '{}'", fullKey);
            log.info("   fullKey length: {}", fullKey.length());
            
            // Check for double slashes
            if (fullKey.contains("//")) {
                log.warn("⚠️  [WARNING] fullKey contains double slashes: '{}'", fullKey);
            }
            
            // ===== DEBUG #4: PutObjectRequest Building =====
            log.info("🔵 [DEBUG #4] Building PutObjectRequest");
            log.info("   Final Bucket: '{}'", cleanBucketName);
            log.info("   Final Key: '{}'", fullKey);
            log.info("   Content Size: {} bytes", content.length);
            log.info("   S3 URL would be: s3://{}/{}", cleanBucketName, fullKey);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(cleanBucketName)
                    .key(fullKey)
                    .build();
            
            log.info("🔵 [DEBUG #5] PutObjectRequest created successfully");
            log.info("   Request Bucket: '{}'", putObjectRequest.bucket());
            log.info("   Request Key: '{}'", putObjectRequest.key());
            
            // ===== DEBUG #6: AWS S3 Upload =====
            log.info("🔵 [DEBUG #6] Attempting S3 PutObject...");
            log.info("   S3Client instance: {}", s3Client != null ? "OK" : "NULL");
            
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
            
            log.info("🔵 [DEBUG #7] S3 PutObject succeeded!");
            
            // ===== DEBUG #8: Success Response =====
            String archiveUrl = "s3://" + cleanBucketName + "/" + fullKey;
            log.info("✅ Successfully archived manuscript!");
            log.info("   Archive URL: {}", archiveUrl);
            log.info("   Bucket: {}", cleanBucketName);
            log.info("   Key: {}", fullKey);
            
            return archiveUrl;
            
        } catch (IllegalArgumentException iae) {
            log.error("❌ [VALIDATION ERROR] Invalid input parameters: {}", iae.getMessage());
            throw new RuntimeException("Archive upload failed - Invalid parameters: " + iae.getMessage(), iae);
            
        } catch (software.amazon.awssdk.services.s3.model.S3Exception s3e) {
            log.error("❌ [S3 ERROR] AWS S3 Exception occurred!");
            log.error("   Error Code: {}", s3e.statusCode());
            log.error("   Error Message: {}", s3e.getMessage());
            log.error("   Request ID: {}", s3e.requestId());
            
            // Specific error handling
            if (s3e.statusCode() == 403) {
                log.error("   🔴 403 FORBIDDEN - Possible causes:");
                log.error("      • Invalid AWS credentials");
                log.error("      • Bucket name is incorrect");
                log.error("      • IAM permissions insufficient");
                log.error("      • Bucket doesn't exist in this region");
            } else if (s3e.statusCode() == 404) {
                log.error("   🔴 404 NOT FOUND - Bucket may not exist");
            }
            
            throw new RuntimeException("Archive upload failed - S3 Error: " + s3e.getMessage(), s3e);
            
        } catch (Exception e) {
            log.error("❌ [GENERIC ERROR] Unexpected error during archive upload!");
            log.error("   Error Type: {}", e.getClass().getName());
            log.error("   Error Message: {}", e.getMessage());
            log.error("   Stack Trace: ", e);
            throw new RuntimeException("Archive upload failed - " + e.getMessage(), e);
        }
    }
}
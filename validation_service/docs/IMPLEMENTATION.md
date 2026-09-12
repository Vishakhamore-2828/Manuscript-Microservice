# Manuscript Validation Service - Implementation Documentation

**Last Updated:** 2026-09-10  
**Version:** 1.0.0  
**Status:** ✅ Implementation Complete

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Component Details](#component-details)
4. [Processing Flow](#processing-flow)
5. [Email Handling](#email-handling)
6. [S3 Archive Storage](#s3-archive-storage)
7. [Activity Tracking](#activity-tracking)
8. [Error Handling](#error-handling)
9. [Test Coverage](#test-coverage)
10. [Configuration](#configuration)
11. [Troubleshooting](#troubleshooting)
12. [Change Log](#change-log)

---

## Overview

The **Manuscript Validation Service** is a Spring Boot application that:
- ✅ Validates manuscript metadata
- ✅ Archives validated documents to S3
- ✅ Sends email notifications to authors
- ✅ Tracks all activities in database
- ✅ Publishes results to SNS topics

### Key Features

| Feature | Status | Details |
|---------|--------|---------|
| Metadata Validation | ✅ Complete | Validates manuscript properties |
| S3 Archiving | ✅ Complete | Archives validated docs to archive bucket |
| Email Notifications | ✅ Complete | Sends pass/fail notifications via SES |
| Activity Tracking | ✅ Complete | Logs all processing steps |
| SNS Publishing | ✅ Complete | Publishes results to topic |
| Error Handling | ✅ Complete | Graceful failure handling |

---

## System Architecture

```
┌─────────────────┐
│  Upload System  │
│   (External)    │
└────────┬────────┘
         │
         ▼
    ┌─────────┐
    │   SNS   │ (File uploaded event)
    └────┬────┘
         │
         ▼
    ┌─────────┐
    │   SQS   │ (Message queue)
    └────┬────┘
         │
         ▼
┌──────────────────────────────┐
│  ValidationProcessor         │ ← Main orchestrator
│  (Apache Camel)              │
└──────────────────────────────┘
         │
    ┌────┴────┬─────────┬──────────┬──────────┐
    ▼         ▼         ▼          ▼          ▼
  ┌──┐    ┌──────┐  ┌──────┐  ┌───────┐  ┌────────┐
  │S3│    │Valid │  │Email │  │Activity│  │  SNS   │
  │  │    │ Service│  │Service│  │Service│  │Topic   │
  └──┘    └──────┘  └──────┘  └───────┘  └────────┘
  Upload   Validation   SES    Database  Publishing
  Bucket
```

---

## Component Details

### 1. ValidationProcessor.java

**Location:** `src/main/java/.../processor/ValidationProcessor.java`

**Responsibility:** Orchestrates the entire validation workflow

**Key Methods:**

#### `process(Exchange exchange)`
Main entry point for processing file upload events.

```java
@Override
public void process(Exchange exchange) throws Exception {
    // 1. Parse SNS message
    // 2. Extract FileUploadedEvent
    // 3. Start validation workflow
    // 4. Handle success/failure cases
    // 5. Publish results
}
```

**Processing Steps:**

| Step | Activity | Details |
|------|----------|---------|
| 1 | `VALIDATION_STARTED` | Log that validation has begun |
| 2 | Run `validateMetadata()` | Validates manuscript properties |
| 3 | Check result | PASSED or FAILED |
| **If PASSED:** | | |
| 4 | `VALIDATION_PASSED` | Log successful validation |
| 5 | Download from S3 | Get manuscript from upload bucket |
| 6 | Archive to S3 | Store in archive bucket |
| 7 | `FILE_ARCHIVED_PASSED` | Log successful archiving |
| 8 | Send email | Notify author of success |
| 9 | `EMAIL_SENT` or `EMAIL_NOT_SENT` | Track email result |
| **If FAILED:** | | |
| 4 | `VALIDATION_FAILED` | Log validation failure |
| 5 | Send email | Notify author of failure |
| 6 | `EMAIL_SENT` or `EMAIL_NOT_SENT` | Track email result |
| 10 | Publish to SNS | Send result to topic |

---

### 2. SesEmailService.java

**Location:** `src/main/java/.../service/SesEmailService.java`

**Responsibility:** Handles email sending via AWS SES

**Key Method:**

```java
public boolean sendEmail(String toEmail, String subject, String body)
```

**Features:**
- ✅ Returns `true` if email sent successfully
- ✅ Returns `false` if email sending fails
- ✅ Logs all email attempts
- ✅ Handles exceptions gracefully

**Email Configuration:**

```properties
aws.ses.from-email=${SES_FROM_EMAIL}
# Example: noreply@manuscriptvalidation.com
```

---

### 3. S3Service.java

**Location:** `src/main/java/.../service/S3Service.java`

**Responsibility:** Handles all S3 operations

**Key Methods:**

```java
// Download from upload bucket
public byte[] downloadManuscript(String requestId, String fileName)

// Upload to archive bucket
public String uploadToArchiveBucket(String bucketName, String key, byte[] content)
```

**Buckets Used:**

| Bucket | Purpose | Path |
|--------|---------|------|
| `upload-bucket` | Temporary upload location | `uploads/{requestId}/` |
| `archive-bucket` | Permanent archive | `archive/{requestId}/{fileName}` |

---

### 4. ActivityService.java

**Location:** `src/main/java/.../service/ActivityService.java`

**Responsibility:** Records all processing activities

**Method:**

```java
public void addActivity(String requestId, ActivityType activityType)
```

**Activity Types:**

```java
VALIDATION_STARTED        // Processing begins
VALIDATION_PASSED         // Metadata validation succeeded
VALIDATION_FAILED         // Metadata validation failed
FILE_ARCHIVED_PASSED      // Successfully archived to S3
FILE_ARCHIVED_FAILED      // Failed to archive
EMAIL_SENT                // Email notification sent
EMAIL_NOT_SENT            // Email not sent (missing or SES failed)
```

---

## Processing Flow

### Success Flow (Validation Passed)

```
1. SNS Event → File uploaded
   ↓
2. VALIDATION_STARTED activity
   ↓
3. Validate metadata
   ✅ Validation PASSED
   ↓
4. VALIDATION_PASSED activity
   ↓
5. Download manuscript from upload S3
   ↓
6. Archive to archive S3
   ✅ s3://archive-bucket/archive/REQ-001/manuscript.pdf
   ↓
7. FILE_ARCHIVED_PASSED activity
   ↓
8. Send success email
   📧 To: author@example.com
   Subject: ✅ Manuscript Validation Passed
   Body: Includes archive URL
   ↓
9. EMAIL_SENT or EMAIL_NOT_SENT activity
   ↓
10. Publish to SNS topic
    ✅ VALIDATION_PASSED status
```

### Failure Flow (Validation Failed)

```
1. SNS Event → File uploaded
   ↓
2. VALIDATION_STARTED activity
   ↓
3. Validate metadata
   ❌ Validation FAILED
   ↓
4. VALIDATION_FAILED activity
   ↓
5. Send failure email
   📧 To: author@example.com
   Subject: ❌ Manuscript Validation Failed
   Body: Include book ID and ISBN
   ↓
6. EMAIL_SENT or EMAIL_NOT_SENT activity
   ↓
7. Publish to SNS topic
   ❌ VALIDATION_FAILED status
```

### Archive Failure Flow

```
1-4. [Same as Success Flow]
   ↓
5. Try to archive to S3
   ❌ Archive FAILED
   ↓
6. FILE_ARCHIVED_FAILED activity
   ↓
7. EMAIL_NOT_SENT activity
   (Email not sent because archive failed)
   ↓
8. Publish error to SNS topic
   ❌ VALIDATION_ERROR status
```

---

## Email Handling

### Email Service Integration

**Service:** `SesEmailService`  
**Provider:** AWS SES (Simple Email Service)

### Success Email (Validation Passed)

**Subject:** `✅ Manuscript Validation Passed`

**Body Template:**
```
Dear {authorId},

Your manuscript has been validated successfully.

Archive Location: {archiveUrl}
Book ID: {bookId}

Best regards,
Validation Service
```

**Example:**
```
Dear AUTH-001,

Your manuscript has been validated successfully.

Archive Location: s3://archive-bucket/archive/REQ-001/manuscript.pdf
Book ID: BOOK-001

Best regards,
Validation Service
```

### Failure Email (Validation Failed)

**Subject:** `❌ Manuscript Validation Failed`

**Body Template:**
```
Dear {authorId},

Your manuscript validation failed. Please check the validation report.

Book ID: {bookId}
ISBN: {isbn}

Best regards,
Validation Service
```

**Example:**
```
Dear AUTH-001,

Your manuscript validation failed. Please check the validation report.

Book ID: BOOK-001
ISBN: 978-3-16-148410-0

Best regards,
Validation Service
```

### Email Status Tracking

```
sendValidationNotification() returns:
├── true  → EMAIL_SENT activity recorded
└── false → EMAIL_NOT_SENT activity recorded

Returns false when:
├── authorEmail is null
├── authorEmail is blank
├── SES sendEmail() returns false
└── Exception occurs during email sending
```

---

## S3 Archive Storage

### Archive Bucket Structure

```
s3://archive-bucket/
└── archive/
    ├── REQ-001/
    │   ├── manuscript.pdf
    │   └── metadata.json
    ├── REQ-002/
    │   └── manuscript.pdf
    └── REQ-003/
        └── manuscript.pdf
```

### Archive Path Format

```
archive/{requestId}/{fileName}
```

**Example:**
```
archive/REQ-TEST-001/manuscript.pdf
```

### Archiving Process

```java
String archivePath = "archive/" + requestId + "/" + fileName;
String archiveUrl = s3Service.uploadToArchiveBucket(
    archiveBucketName,  // "archive-bucket-name"
    archivePath,        // "archive/REQ-001/manuscript.pdf"
    manuscriptContent   // byte[] of file
);
// Returns: s3://archive-bucket/archive/REQ-001/manuscript.pdf
```

### Archive URL Inclusion

Archive URL is:
- ✅ Returned from `archiveValidatedManuscript()`
- ✅ Passed to `sendValidationNotification()`
- ✅ Included in email body
- ✅ Available for download by authors

---

## Activity Tracking

### Activity Recording Flow

```java
activityService.addActivity(requestId, ActivityType.VALIDATION_STARTED);
// Saved to database with:
// - Request ID
// - Activity Type
// - Timestamp
// - Status (SUCCESS/FAILURE)
```

### Complete Activity Sequence (Success Case)

```
1. VALIDATION_STARTED       → Processing begins
2. VALIDATION_PASSED        → Metadata valid
3. FILE_ARCHIVED_PASSED     → Archived to S3
4. EMAIL_SENT               → Email notification sent
```

### Complete Activity Sequence (Failure Case)

```
1. VALIDATION_STARTED       → Processing begins
2. VALIDATION_FAILED        → Metadata invalid
3. EMAIL_SENT               → Failure notification sent
```

### Complete Activity Sequence (Archive Failure)

```
1. VALIDATION_STARTED       → Processing begins
2. VALIDATION_PASSED        → Metadata valid
3. FILE_ARCHIVED_FAILED     → S3 upload failed
4. EMAIL_NOT_SENT           → Email not sent (archive failed)
```

### Query Activities

```java
// Retrieve all activities for a request
List<Activity> activities = activityRepository.findByRequestId("REQ-001");

// Activities visible in:
// ├── Database table: activities
// ├── Application logs
// └── Dashboard/UI
```

---

## Error Handling

### Error Scenarios

#### 1. Email Missing

**Condition:** `authorEmail == null || authorEmail.isBlank()`

**Behavior:**
```
⚠️  Author email is missing for requestId: REQ-001
❌ EMAIL_NOT_SENT activity recorded
```

**Email Sent:** ❌ NO  
**Activity:** `EMAIL_NOT_SENT`  
**Result:** Process continues

---

#### 2. SES Email Failure

**Condition:** `sesEmailService.sendEmail() returns false`

**Behavior:**
```
⚠️  Email failed to send for requestId: REQ-001 to author@example.com
❌ EMAIL_NOT_SENT activity recorded
```

**Email Sent:** ❌ NO  
**Activity:** `EMAIL_NOT_SENT`  
**Result:** Process continues

---

#### 3. S3 Archive Failure

**Condition:** `s3Service.uploadToArchiveBucket() throws exception`

**Behavior:**
```
❌ Archive failed: {error message}
❌ FILE_ARCHIVED_FAILED activity recorded
❌ EMAIL_NOT_SENT activity recorded
❌ Exception thrown to Camel
```

**Email Sent:** ❌ NO (archive failed first)  
**Activities:** `FILE_ARCHIVED_FAILED`, `EMAIL_NOT_SENT`  
**Result:** Exception propagated, message sent to DLQ

---

#### 4. General Exception

**Condition:** Any unexpected error in `process()`

**Behavior:**
```
❌ Error during validation: {error message}
❌ VALIDATION_FAILED activity recorded
❌ Error published to SNS
```

**Activities:** `VALIDATION_FAILED`  
**Result:** Exception thrown, message sent to DLQ

---

### Exception Handling Code

```java
try {
    // Main processing
    validationResult = validationService.validateMetadata(event);
    
    if (validationResult.isPassed()) {
        // Success path
        ...
    } else {
        // Failure path
        ...
    }
    
} catch (Exception e) {
    logger.error("❌ Error during validation: {}", e.getMessage(), e);
    
    // Record failure
    activityService.addActivity(requestId, ActivityType.VALIDATION_FAILED);
    
    try {
        // Publish error to SNS
        publishValidationErrorToSNS(requestId, event, e);
    } catch (Exception snsError) {
        logger.error("Failed to publish error to SNS");
    }
    
    // Re-throw for Camel error handling
    throw e;
}
```

---

## Test Coverage

### Test File Location

```
src/test/java/.../processor/ValidationProcessorTest.java
```

### Test Cases

#### Test 1: ✅ Validation Passed + Archive Success + Email Sent

```java
testValidationPassedArchiveSuccessEmailSent()
```

**Scenario:**
- Validation: ✅ PASSED
- Archive: ✅ SUCCESS
- Email: ✅ SENT

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_PASSED`
3. `FILE_ARCHIVED_PASSED`
4. `EMAIL_SENT`

**Status:** ✅ PASS

---

#### Test 2: ✅ Validation Passed + Archive Success + Email Fails

```java
testValidationPassedArchiveSuccessEmailFails()
```

**Scenario:**
- Validation: ✅ PASSED
- Archive: ✅ SUCCESS
- Email: ❌ FAILED

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_PASSED`
3. `FILE_ARCHIVED_PASSED`
4. `EMAIL_NOT_SENT`

**Status:** ✅ PASS

---

#### Test 3: ✅ Validation Passed + Archive Success + Email Missing

```java
testValidationPassedArchiveSuccessEmailMissing()
```

**Scenario:**
- Validation: ✅ PASSED
- Archive: ✅ SUCCESS
- Email: 🚫 MISSING

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_PASSED`
3. `FILE_ARCHIVED_PASSED`
4. `EMAIL_NOT_SENT`

**SES Call:** ❌ Never made  
**Status:** ✅ PASS

---

#### Test 4: ✅ Validation Passed + Archive Fails

```java
testValidationPassedArchiveFails()
```

**Scenario:**
- Validation: ✅ PASSED
- Archive: ❌ FAILED
- Email: ❌ NOT ATTEMPTED

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_PASSED`
3. `FILE_ARCHIVED_FAILED`
4. `EMAIL_NOT_SENT`

**SES Call:** ❌ Never made  
**Exception:** ✅ Thrown  
**Status:** ✅ PASS

---

#### Test 5: ❌ Validation Failed + Email Sent

```java
testValidationFailedEmailSent()
```

**Scenario:**
- Validation: ❌ FAILED
- Email: ✅ SENT

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_FAILED`
3. `EMAIL_SENT`

**S3 Operations:** ❌ Skipped  
**Status:** ✅ PASS

---

#### Test 6: ❌ Validation Failed + Email Fails

```java
testValidationFailedEmailFails()
```

**Scenario:**
- Validation: ❌ FAILED
- Email: ❌ FAILED

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_FAILED`
3. `EMAIL_NOT_SENT`

**Status:** ✅ PASS

---

#### Test 7: ❌ Validation Failed + Email Missing

```java
testValidationFailedEmailMissing()
```

**Scenario:**
- Validation: ❌ FAILED
- Email: 🚫 MISSING

**Expected Activities:**
1. `VALIDATION_STARTED`
2. `VALIDATION_FAILED`
3. `EMAIL_NOT_SENT`

**SES Call:** ❌ Never made  
**Status:** ✅ PASS

---

#### Test 8: Email Content Verification (Success)

```java
testEmailContentForPassedValidation()
```

**Verifies:**
- Subject: `✅ Manuscript Validation Passed`
- Body: Contains `BOOK-001` and `archive`

**Status:** ✅ PASS

---

#### Test 9: Email Content Verification (Failure)

```java
testEmailContentForFailedValidation()
```

**Verifies:**
- Subject: `❌ Manuscript Validation Failed`
- Body: Contains `BOOK-001` and `978-3-16-148410-0` (ISBN)

**Status:** ✅ PASS

---

### Running Tests

```powershell
# Run all ValidationProcessor tests
.\gradlew.bat test --tests ValidationProcessorTest -v

# Run specific test
.\gradlew.bat test --tests ValidationProcessorTest.testValidationPassedArchiveSuccessEmailSent -v

# Run with coverage
.\gradlew.bat test --tests ValidationProcessorTest jacocoTestReport
```

### Expected Result

```
BUILD SUCCESSFUL
9 tests completed, 0 failed ✅
```

---

## Configuration

### Application Properties

**File:** `application.yml` or `application.properties`

```yaml
# AWS Configuration
aws:
  region: ap-south-1
  
  # SNS Configuration
  sns:
    validation-result-topic-arn: arn:aws:sns:ap-south-1:206465504931:manuscript-validation-result
  
  # S3 Configuration
  s3:
    upload-bucket-name: upload-bucket
    archive-bucket-name: archive-bucket-name
  
  # SES Configuration
  ses:
    from-email: noreply@manuscriptvalidation.com

# Camel Configuration
camel:
  component:
    aws-sqs:
      queue-name: validation-queue
      endpoint: https://sqs.ap-south-1.amazonaws.com/206465504931/validation-queue

# Logging
logging:
  level:
    root: INFO
    com.manuscriptvalidation: DEBUG
```

### Environment Variables

```bash
export AWS_REGION=ap-south-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export SES_FROM_EMAIL=noreply@manuscriptvalidation.com
```

---

## Troubleshooting

### Issue 1: "Author email is missing"

**Log Message:**
```
⚠️  Author email is missing for requestId: REQ-001
```

**Cause:** `FileUploadedEvent.authorEmail` is null or blank

**Solution:**
```
1. Verify upload system sends authorEmail
2. Check FileUploadedEvent class has @JsonProperty("authorEmail")
3. Validate SNS message contains authorEmail field
```

---

### Issue 2: "Email failed to send"

**Log Message:**
```
⚠️  Email failed to send for requestId: REQ-001 to author@example.com
```

**Cause:** SES returned false (email invalid, quota exceeded, etc.)

**Solution:**
```
1. Verify email address format
2. Check SES account is not in sandbox mode
3. Verify sender email is verified in SES
4. Check SES daily quota
5. Review SES logs in CloudWatch
```

---

### Issue 3: "Archive failed"

**Log Message:**
```
❌ Archive failed: Access Denied
```

**Cause:** S3 permissions issue

**Solution:**
```
1. Verify IAM role has S3 permissions
2. Check archive bucket name in config
3. Verify archive bucket exists
4. Check bucket policies allow uploads
```

---

### Issue 4: "Validation Processor not found"

**Log Message:**
```
error: cannot find symbol: class ValidationProcessor
```

**Cause:** File not in correct package

**Solution:**
```
Ensure file location:
c:\...\src\main\java\com\manuscriptvalidation\validation_service\processor\ValidationProcessor.java
```

---

### Issue 5: Test failures with NullPointerException

**Error:**
```
java.lang.NullPointerException at ValidationProcessorTest.java:121
```

**Cause:** Mocked dependencies returning null

**Solution:**
```java
// Use setupObjectMapperMocks() before each test
setupObjectMapperMocks(testEvent);
```

---

## Change Log

### Version 1.0.0 (2026-09-10)

#### ✅ New Features

1. **Email Status Tracking**
   - `SesEmailService.sendEmail()` now returns `boolean`
   - Email success/failure properly tracked
   - `EMAIL_SENT` or `EMAIL_NOT_SENT` activities recorded

2. **S3 Archive Integration**
   - Validated manuscripts automatically archived to S3
   - Archive URL included in success emails
   - Archive structure: `s3://archive-bucket/archive/{requestId}/{fileName}`

3. **Comprehensive Activity Logging**
   - 7 activity types tracked: `VALIDATION_STARTED`, `VALIDATION_PASSED`, `VALIDATION_FAILED`, `FILE_ARCHIVED_PASSED`, `FILE_ARCHIVED_FAILED`, `EMAIL_SENT`, `EMAIL_NOT_SENT`
   - All activities recorded in database
   - Complete audit trail available

4. **Robust Error Handling**
   - Missing email addresses handled gracefully
   - Archive failures prevent email sending
   - All exceptions logged and published to SNS
   - DLQ support for failed messages

5. **Comprehensive Test Suite**
   - 9 unit tests covering all scenarios
   - Tests for success, failure, and edge cases
   - Email content verification
   - Mock-based testing with Mockito

#### 📝 Modified Components

| Component | Changes |
|-----------|---------|
| `SesEmailService.java` | Changed return type from `void` to `boolean` |
| `ValidationProcessor.java` | Added email status tracking, archive on success, activity recording |
| `ValidationProcessor.java` | New test coverage with 9 test cases |

#### 🔄 Processing Changes

**Before:**
```
Validation → Email → SNS
(No archive, no email status tracking)
```

**After:**
```
Validation → Archive → Email → SNS → Activity Log
(Complete tracking, archive integration)
```

---

## Future Enhancements

### Planned Features

- [ ] Webhook notifications (alternative to email)
- [ ] Retry logic for failed emails
- [ ] Email template customization
- [ ] Archive lifecycle policies (expiration)
- [ ] Detailed validation reports
- [ ] API for activity history retrieval
- [ ] Dashboard for monitoring
- [ ] Metrics and analytics

---

## Support & Maintenance

### Known Limitations

1. Archive is permanent (no automatic cleanup)
2. Email retry count is 0 (no retries)
3. Single email template (no customization)
4. No webhook support yet

### Contact

For issues or questions about this implementation:

1. Check logs: `logs/application.log`
2. Review CloudWatch: AWS Console
3. Check SQS DLQ: Failed messages
4. Review database activities: Activities table

---

## Quick Reference

### Key Files

```
Main Implementation:
├── src/main/java/.../processor/ValidationProcessor.java      (Main orchestrator)
├── src/main/java/.../service/SesEmailService.java           (Email service)
├── src/main/java/.../service/S3Service.java                 (S3 operations)
├── src/main/java/.../service/ValidationService.java         (Validation logic)
└── src/main/java/.../service/ActivityService.java           (Activity tracking)

Tests:
└── src/test/java/.../processor/ValidationProcessorTest.java  (9 test cases)

Configuration:
└── src/main/resources/application.yml                        (Config file)
```

### Activity Codes

```
01 = VALIDATION_STARTED
02 = VALIDATION_PASSED
03 = VALIDATION_FAILED
04 = FILE_ARCHIVED_PASSED
05 = FILE_ARCHIVED_FAILED
06 = EMAIL_SENT
07 = EMAIL_NOT_SENT
```

### Email Endpoints

```
From: noreply@manuscriptvalidation.com (configured)
To: {event.authorEmail} (from upload event)
Subject: ✅/❌ Manuscript Validation {Passed/Failed}
```

### S3 Paths

```
Upload Bucket:  s3://upload-bucket/uploads/{requestId}/{fileName}
Archive Bucket: s3://archive-bucket/archive/{requestId}/{fileName}
```

---

**End of Documentation**  
**Version:** 1.0.0  
**Last Updated:** 2026-09-10
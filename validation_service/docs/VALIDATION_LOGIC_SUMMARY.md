# Manuscript Validation Service - 6 Validation Rules

## Overview
The validation-service implements **6 comprehensive validations** to ensure manuscript uploads are valid before archiving. All validations run sequentially, and if any fail, the process stops and a failure notification is sent.

---

## Validation Pipeline

### **1️⃣ RequiredMetadataValidator** ✅
**Location:** [RequiredMetadataValidator.java](../src/main/java/com/manuscriptvalidation/validation_service/validation/RequiredMetadataValidator.java)

**Purpose:** Validates that all required metadata fields are present and valid

**Logic:**
```
✓ REQUIRED Fields (must NOT be blank):
  - requestId         → Unique request identifier
  - bookId            → Book identifier
  - bookName          → Title of the manuscript
  - authorId          → Author's user ID
  - authorEmail       → Author's email (for notifications)
  - s3Reference       → S3 path to manuscript file

✓ OPTIONAL Fields (validated only if present):
  - isbn              → Book ISBN number
    - Supports ISBN-10: (10×d₁ + 9×d₂ + ... + 1×d₁₀) mod 11 = 0
    - Supports ISBN-13: Σ(digit × weight) mod 10 = 0 (weights alternate 1,3)
    - Last digit of ISBN-10 can be 'X' (represents 10)
```

**When it runs:** STEP 1 - Before any file operations

**On Failure:** Returns VALIDATION_FAILED + sends failure notification

**Example:**
```json
✅ PASS: { requestId: "REQ-0036", bookId: "BOOK-001", isbn: "9780306406157" }
❌ FAIL: { requestId: null } → Missing request ID
❌ FAIL: { isbn: "1234567890" } → Invalid ISBN-10 checksum
```

---

### **2️⃣ FileExtensionValidator** ✅
**Location:** [FileExtensionValidator.java](../src/main/java/com/manuscriptvalidation/validation_service/validation/FileExtensionValidator.java)

**Purpose:** Ensures file has one of the allowed manuscript formats

**Logic:**
```
✓ Allowed Extensions:
  - .pdf   → PDF documents
  - .docx  → Microsoft Word documents
  - .epub  → EPUB e-books

✓ Validation Process:
  1. Extract extension from S3 reference path
  2. Convert to lowercase for case-insensitive comparison
  3. Check if extension is in ALLOWED_EXTENSIONS set
```

**When it runs:** STEP 2 - After metadata validation, before download

**On Failure:** Returns VALIDATION_FAILED + sends failure notification

**Example:**
```
S3 Reference: s3://bucket/injection/REQ-0036/lewis-lion.epub
Extracted:    .epub
✅ PASS: .epub is allowed
❌ FAIL: .txt would be rejected
```

---

### **3️⃣ FileNameValidator** ✅
**Location:** [FileNameValidator.java](../src/main/java/com/manuscriptvalidation/validation_service/validation/FileNameValidator.java)

**Purpose:** Validates that the file name is valid and safe

**Logic:**
```
✓ File name must NOT be:
  - Empty or blank
  - Contain path separators (/ or \)
  - Contain null bytes (\0)
  - Be just dots (. or ..)
  - Contain control characters

✓ Valid file name:
  - Must have at least one character
  - Must not be a directory reference
  - Must be a simple file name
```

**When it runs:** STEP 3 - After file extension validation

**On Failure:** Returns VALIDATION_FAILED + sends failure notification

**Example:**
```
✅ PASS: lewis-lion.epub, manuscript_v2.pdf
❌ FAIL: ../../../etc/passwd (path traversal)
❌ FAIL: .  or  .. (directory reference)
```

---

### **4️⃣ FileExistenceValidator** ✅
**Location:** [FileExistenceValidator.java](../src/main/java/com/manuscriptvalidation/validation_service/validation/FileExistenceValidator.java)

**Purpose:** Confirms that the downloaded file has content

**Logic:**
```
✓ File existence check:
  1. Download manuscript content from S3
  2. Verify content is not null
  3. Verify content.length > 0
  4. If either check fails → file doesn't exist or is empty
```

**When it runs:** STEP 4 - After file is downloaded from S3

**On Failure:** Returns VALIDATION_FAILED + sends failure notification

**Example:**
```
✓ Downloaded: byte[2048] → Content exists
❌ Downloaded: byte[0] → File is empty
❌ Downloaded: null → S3 returned nothing
```

---

### **5️⃣ FileSizeValidator** ✅
**Location:** [FileSizeValidator.java](../src/main/java/com/manuscriptvalidation/validation_service/validation/FileSizeValidator.java)

**Purpose:** Ensures file doesn't exceed size limits (protection against storage overflow)

**Logic:**
```
✓ Maximum file size:
  - MAX_FILE_SIZE = 10 MB (10 × 1024 × 1024 bytes)

✓ Validation logic:
  1. Check if file content length > 0
  2. Check if file content length ≤ 10 MB
  3. If exceeds limit → reject with error message showing actual size
```

**When it runs:** STEP 5 - After file existence validation

**On Failure:** Returns VALIDATION_FAILED + sends failure notification + shows actual file size

**Example:**
```
✅ PASS: 2.5 MB manuscript
❌ FAIL: 15 MB file → exceeds 10 MB limit (error shows "15.00 MB exceeds 10.00 MB")
```

---

### **6️⃣ FileCorruptionValidator** ✅
**Location:** [FileCorruptionValidator.java](../src/main/java/com/manuscriptvalidation/validation_service/validation/FileCorruptionValidator.java)

**Purpose:** Verifies file integrity by attempting to read the entire content stream

**Logic:**
```
✓ Corruption detection:
  1. Wrap downloaded bytes in ByteArrayInputStream
  2. Read entire file in 8KB chunks
  3. Verify total bytes read equals original file size
  4. Catch any IOExceptions (indicates corruption or unreadable format)

✓ Validates:
  - File is readable as a byte stream
  - No exceptions occur during sequential read
  - All bytes can be read successfully
  - File size matches expected length
```

**When it runs:** STEP 6 - After file size validation

**On Failure:** Returns VALIDATION_FAILED + sends failure notification

**Example:**
```
✅ PASS: File reads completely without errors
❌ FAIL: IOException during read → File is corrupted
❌ FAIL: Read mismatch → 1000 bytes expected but 900 bytes read
```

---

## Complete Validation Flow

```
SQS Message Received (FileUploadedEvent)
         ↓
    VALIDATION_STARTED activity recorded
         ↓
    ┌─────────────────────────────────────────┐
    │  1. RequiredMetadataValidator            │
    │     (requestId, bookId, email, ISBN)     │
    └─────────────────────────────────────────┘
         ↓ [✅ PASS]
    ┌─────────────────────────────────────────┐
    │  2. FileExtensionValidator               │
    │     (extract from S3 reference)          │
    └─────────────────────────────────────────┘
         ↓ [✅ PASS]
    ┌─────────────────────────────────────────┐
    │  3. FileNameValidator                    │
    │     (check for path traversal, special)  │
    └─────────────────────────────────────────┘
         ↓ [✅ PASS]
         ↓ Download File from S3
         ↓
    ┌─────────────────────────────────────────┐
    │  4. FileExistenceValidator               │
    │     (verify content is not empty)        │
    └─────────────────────────────────────────┘
         ↓ [✅ PASS]
    ┌─────────────────────────────────────────┐
    │  5. FileSizeValidator                    │
    │     (check < 10 MB limit)                │
    └─────────────────────────────────────────┘
         ↓ [✅ PASS]
    ┌─────────────────────────────────────────┐
    │  6. FileCorruptionValidator              │
    │     (read entire stream, check integrity)│
    └─────────────────────────────────────────┘
         ↓ [✅ PASS]
    VALIDATION_PASSED activity recorded
         ↓
    Archive to S3
         ↓
    FILE_ARCHIVED_PASSED activity recorded
         ↓
    Send Success Email + SNS Publish
         ↓ EMAIL_SENT / FILE_ARCHIVED_PASSED

    [❌ ANY VALIDATION FAILS]
         ↓
    VALIDATION_FAILED activity recorded
         ↓
    Send Failure Email + SNS Publish
         ↓ EMAIL_SENT / EMAIL_NOT_SENT
```

---

## Database Activity Tracking

Each validation step records activities in MongoDB:

| Step | Activity Type | Condition | Database Record |
|------|---|---|---|
| Start | `VALIDATION_STARTED` | Always | Recorded before any validation |
| Metadata | `VALIDATION_FAILED` | ❌ Fails | Recorded if required fields missing |
| Extension | `VALIDATION_FAILED` | ❌ Fails | Recorded if extension not allowed |
| FileName | `VALIDATION_FAILED` | ❌ Fails | Recorded if name is invalid |
| Existence | `VALIDATION_FAILED` | ❌ Fails | Recorded if file empty/missing |
| FileSize | `VALIDATION_FAILED` | ❌ Fails | Recorded if > 10 MB |
| Corruption | `VALIDATION_FAILED` | ❌ Fails | Recorded if unreadable |
| All Pass | `VALIDATION_PASSED` | ✅ All pass | Recorded after all 6 pass |
| Archive | `FILE_ARCHIVED_PASSED` | ✅ Success | Recorded if S3 upload succeeds |
| Email | `EMAIL_SENT` | ✅ Success | Recorded if email delivery succeeds |

---

## Key Fixes Applied

### Fix #1: RequiredMetadataValidator - Comprehensive ISBN
- **Before:** Only checked if ISBN had 10 or 13 digits
- **After:** Validates ISBN-10 and ISBN-13 checksums using industry-standard formulas
- **Impact:** Now properly rejects invalid ISBNs like "9789396055024" (wrong checksum)

### Fix #2: FileExtensionValidator - Already Correct ✅
- Properly extracts extension from S3 path
- Case-insensitive comparison
- Allows .pdf, .docx, .epub only

### Fix #3: FileSizeValidator - Adapted for S3
- **Before:** Accepted only MultipartFile
- **After:** Accepts byte array (downloaded file content) or file size in bytes
- **Added:** Human-readable file size formatting (KB, MB, GB)

### Fix #4: FileNameValidator - S3 Ready
- **Before:** Accepted only MultipartFile.getOriginalFilename()
- **After:** Accepts string file name, validates for path traversal and special chars
- **Added:** Protection against directory traversal attacks (../, .., .)

### Fix #5: FileExistenceValidator - S3 Ready
- **Before:** Accepted only MultipartFile
- **After:** Accepts byte array content, checks if not null and not empty
- **Usage:** Verifies file was successfully downloaded from S3

### Fix #6: FileCorruptionValidator - S3 Ready
- **Before:** Accepted only MultipartFile.getInputStream()
- **After:** Accepts byte array, wraps in ByteArrayInputStream, reads sequentially
- **Verification:** Confirms all bytes readable + length matches

### Fix #7: IsbnValidator - Aligned with RequiredMetadata
- **Before:** Only validated ISBN-13, required ISBN to be present
- **After:** Supports both ISBN-10 and ISBN-13, ISBN is optional
- **Now:** Consistent with RequiredMetadataValidator logic

---

## Integration into ValidationProcessor

The ValidationProcessor now calls all 6 validators in sequence:

```java
// Step 1: Validate required metadata
validationResult = validationService.validateMetadata(event);

// Step 2: Validate file extension
ValidationResultDto fileValidation = validationService.validateFileExtension(event.getS3Reference());

// Step 3: Validate file name
ValidationResultDto fileNameValidation = validationService.validateFileName(fileName);

// Step 4: Download file from S3
byte[] manuscriptContent = s3Service.downloadManuscriptByPath(s3Path);

// Step 5: Validate file existence
ValidationResultDto fileExistenceValidation = validationService.validateFileExistence(manuscriptContent);

// Step 6: Validate file size
ValidationResultDto fileSizeValidation = validationService.validateFileSize(manuscriptContent);

// Step 7: Validate file corruption
ValidationResultDto fileCorruptionValidation = validationService.validateFileCorruption(manuscriptContent);
```

Each validator failure causes immediate return and error notification before proceeding to next step.

---

## Testing the 6 Validations

### Test Case 1: Valid Manuscript
```json
{
  "requestId": "REQ-TEST-001",
  "bookId": "BOOK-TEST-001",
  "bookName": "Test Manuscript",
  "authorId": "AUTH-001",
  "authorEmail": "test@example.com",
  "isbn": "9780306406157",
  "s3Reference": "injection/REQ-TEST-001/manuscript.epub"
}
```
**Expected Result:** ✅ All 6 validations PASS → FILE_ARCHIVED_PASSED

### Test Case 2: Missing Email (Fails #1)
```json
{
  "requestId": "REQ-TEST-002",
  "bookId": "BOOK-TEST-001",
  "authorEmail": null  // ❌ Missing
}
```
**Expected Result:** ❌ VALIDATION_FAILED at RequiredMetadataValidator

### Test Case 3: Invalid Extension (Fails #2)
```json
{
  "s3Reference": "injection/REQ-TEST-003/manuscript.txt"  // ❌ .txt not allowed
}
```
**Expected Result:** ❌ VALIDATION_FAILED at FileExtensionValidator

### Test Case 4: Invalid File Name (Fails #3)
```json
{
  "s3Reference": "injection/REQ-TEST-004/../../../etc/passwd"  // ❌ Path traversal
}
```
**Expected Result:** ❌ VALIDATION_FAILED at FileNameValidator

### Test Case 5: File Too Large (Fails #5)
```
Downloaded file: 15 MB (exceeds 10 MB limit)
```
**Expected Result:** ❌ VALIDATION_FAILED at FileSizeValidator

### Test Case 6: Corrupted File (Fails #6)
```
Downloaded file: Corrupted binary → IOException on read
```
**Expected Result:** ❌ VALIDATION_FAILED at FileCorruptionValidator

---

## Summary

| Validator | Type | Input | Output | Run Order |
|-----------|------|-------|--------|-----------|
| RequiredMetadata | Metadata | FileUploadedEvent | ValidationResult | #1 |
| FileExtension | File | S3 Reference | ValidationResult | #2 |
| FileName | File | String fileName | ValidationResult | #3 |
| FileExistence | File | byte[] content | ValidationResult | #4 |
| FileSize | File | byte[] content | ValidationResult | #5 |
| FileCorruption | File | byte[] content | ValidationResult | #6 |

**All validations must pass for the manuscript to be archived. Any failure stops processing and sends failure notification to author.**

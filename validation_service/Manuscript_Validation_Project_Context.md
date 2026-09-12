# Manuscript Validation Project --- Complete Context & Mentorship Notes

## Purpose of this document

This document is the consolidated context for the **Manuscript
Validation / validation_service** project.

It combines the important decisions, learning/mentorship plan,
architecture, implementation progress, issues encountered, current code
structure, AWS/MongoDB setup, and the exact next coding direction
discussed across the **Project Mentorship Plan** and **Coding Baseline /
Continue Coding** conversations.

### How this document should be used

When continuing this project in VS Code / Copilot / ChatGPT:

-   Treat this document as the current project context.
-   Do **not** restart completed learning or implementation.
-   Do **not** replace agreed architecture with a different
    architecture.
-   Do **not** invent missing business requirements.
-   Work **one step at a time** when implementing code.
-   Give exact file names and exact code changes.
-   Keep explanations simple and practical.
-   Preserve the existing project decisions unless a new explicit
    requirement changes them.
-   If something is marked **PENDING / UNCONFIRMED**, do not guess it.

------------------------------------------------------------------------

# 1. Project Overview

## Project name

**Manuscript Validation**

Current Spring Boot project:

``` text
validation_service
```

This is a Java Spring Boot microservice responsible for validating a
manuscript after it has been uploaded.

The user is working primarily on the **Validation Service**. Another
colleague is responsible for the **Upload Service**.

------------------------------------------------------------------------

# 2. Main Business Goal

The system receives information about an uploaded manuscript.

The Validation Service must:

1.  Receive the upload event.
2.  Read the manuscript from S3.
3.  Run all required validations.
4.  Track the status of every validation.
5.  Store validation status/results in MongoDB.
6.  Update activities for the request.
7.  Produce a final PASS/FAIL outcome.
8.  Later publish the validation result/event.
9.  Later trigger email notification through AWS SES.

The validation checks currently identified are:

1.  ISBN-13
2.  File exists
3.  File extension
4.  File size
5.  File is not corrupted
6.  Filename rule
7.  Required metadata

Important:

> All seven validations should execute. One failed validation must NOT
> stop the remaining validations.

------------------------------------------------------------------------

# 3. Final Architecture Decision

The final event-driven architecture is:

``` text
                 Upload Service
                       |
                       v
                      S3
                       |
                       v
               S3 Event Notification
                       |
                       v
                      SNS
                       |
                       v
                      SQS
                       |
                       v
             Validation Service
                       |
                       v
                 Camel Route
                       |
                       v
             ValidationProcessor
                       |
                       v
              ValidationService
                       |
                       v
                  S3Service
                       |
                       v
                  Validators
                       |
                       v
                  MongoDB
                       |
              +--------+--------+
              |                 |
              v                 v
          PASS result       FAIL result
              |                 |
              +--------+--------+
                       |
                       v
                      SES
```

## Critical architecture decision

The Validation Service does **not** directly consume S3 Event
Notifications.

The final messaging flow is:

``` text
S3 -> SNS -> SQS -> Validation Service
```

Do not change this back to:

``` text
S3 -> SQS
```

and do not make the Validation Service directly responsible for
consuming S3 events.

SNS is the final selected messaging component between S3 and SQS.

------------------------------------------------------------------------

# 4. Responsibilities

## Upload Service

Handled by the colleague.

Expected responsibility:

-   Receive/upload manuscript.
-   Store manuscript in S3.
-   Create/upload event information.
-   Publish/trigger the event flow.

## Validation Service

Handled by the user.

Responsibilities:

-   Consume the SQS message.
-   Deserialize the event.
-   Fetch/read the manuscript from S3.
-   Run validations.
-   Update validation statuses.
-   Update request activities.
-   Store results in MongoDB.
-   Produce final validation outcome.
-   Later publish PASS/FAIL event.
-   Later integrate AWS SES for notification.

------------------------------------------------------------------------

# 5. Mentorship / Learning Plan

The project was intentionally approached as both:

1.  A real implementation project.
2.  A learning/mentorship exercise for Java, Spring Boot, MongoDB, AWS,
    Camel, and microservice architecture.

The learning sequence completed so far was:

## Part 1 --- Project & Architecture

Covered:

-   What the Manuscript Validation project does.
-   Microservice responsibilities.
-   Upload Service vs Validation Service.
-   Event-driven architecture.
-   S3.
-   SNS.
-   SQS.
-   MongoDB.
-   AWS SES.
-   Why asynchronous messaging is useful.
-   Overall request/event flow.

## Part 2 --- Spring Boot & Setup

Covered:

-   Spring Boot application structure.
-   Controllers.
-   Services.
-   Components.
-   Dependency injection.
-   DTOs.
-   Repositories.
-   Configuration.
-   Gradle.
-   Application properties/YAML.
-   Basic Spring Boot testing.

## Part 3 --- MongoDB

Covered:

-   MongoDB basics.
-   Documents and collections.
-   Spring Data MongoDB.
-   `@Document`.
-   `@Id`.
-   `@Indexed`.
-   MongoRepository.
-   Repository query methods.
-   Validation status persistence.
-   Duplicate request ID problem.
-   Unique index for `requestId`.

A real issue was encountered:

``` text
IncorrectResultSizeDataAccessException
```

because multiple records existed for the same request ID.

The solution was to make `requestId` unique:

``` java
@Indexed(unique = true)
private String requestId;
```

## Part 4 --- Validation Architecture

Covered:

-   Validator classes.
-   Separation of validation responsibilities.
-   ValidationResultDto.
-   ValidationErrorDto.
-   ValidationStatus.
-   ValidationStatusDocument.
-   Running all validations independently.
-   Collecting validation errors.
-   Updating individual validation status.

## Part 5 --- AWS Fundamentals

Covered:

-   S3.
-   SNS.
-   SQS.
-   AWS credentials.
-   AWS region.
-   AWS CLI.
-   AWS SSO.
-   Default AWS credential provider chain.
-   Event-driven communication.

## Part 6 --- Apache Camel / Messaging Integration

The project then moved from learning back into implementation.

Current Camel flow:

``` text
SQS
 |
 v
Camel Route
 |
 v
ValidationProcessor
 |
 v
FileUploadedEvent
```

This part is implemented and working.

------------------------------------------------------------------------

# 6. Current AWS Environment

AWS region:

``` text
ap-south-1
```

AWS account:

``` text
206465504931
```

S3 bucket:

``` text
book-platform-files-206465504931-ap-south-1-an
```

SQS queue:

``` text
manuscript-validation-queue
```

SQS URL:

``` text
https://sqs.ap-south-1.amazonaws.com/206465504931/manuscript-validation-queue
```

AWS CLI authentication uses AWS SSO.

AWS profile:

``` text
manuscript-dev
```

Role:

``` text
ManuscriptDeveloperAccess
```

Authentication has already been tested successfully.

## Important credential rule

Do NOT hardcode:

``` text
AWS access key
AWS secret key
AWS session token
```

The application should use the AWS SDK default credential provider
chain.

The configured AWS SSO profile should provide credentials when running
in the appropriate environment.

------------------------------------------------------------------------

# 7. Current MongoDB Environment

MongoDB Atlas is being used.

Database:

``` text
book_management
```

Validation collection:

``` text
Validation_Status
```

The MongoDB connection is working.

------------------------------------------------------------------------

# 8. Actual S3 Object Example

An actual object currently available in S3 is:

``` text
injection/REQ-0003/lewis-lion-the-witch-and-the-wardrobe.epub
```

Observed metadata:

``` text
Content-Type: application/epub+zip
Size: 184,706 bytes
```

This confirmed that the Validation Service can use the S3 reference from
the event to locate the manuscript.

------------------------------------------------------------------------

# 9. Actual Upload Event / MongoDB Contract

The latest actual Upload Service MongoDB document contains fields such
as:

``` json
{
  "requestId": "...",
  "bookId": "...",
  "authorId": "...",
  "fileFormat": "...",
  "s3Reference": "...",
  "activities": [],
  "createdAt": "...",
  "updatedAt": "..."
}
```

Important:

-   Current contract uses `bookId`.
-   Do not add `bookName` back unless the Upload Service contract
    explicitly changes.
-   The latest actual document does NOT currently show ISBN.
-   ISBN source is still pending confirmation.

## ISBN is PENDING

The exact source of ISBN has not yet been confirmed.

Therefore:

> Do NOT invent whether ISBN comes from the event, MongoDB, S3 metadata,
> filename, manuscript content, or another service.

The existing ISBN validator expects an ISBN from the event, but the
current `FileUploadedEvent` does not yet contain ISBN.

This needs to be resolved with the Upload Service / mentor before
finalizing the ISBN implementation.

------------------------------------------------------------------------

# 10. Current Project Structure

The project uses the following conceptual layers:

``` text
Controller
   |
Service
   |
Validator
   |
Repository
```

For event processing:

``` text
SQS
   |
Camel Route
   |
Processor
   |
Service
   |
S3 Service
   |
Validators
   |
Repository
```

The project uses:

-   Spring Boot
-   Java
-   Gradle
-   Spring Data MongoDB
-   AWS SDK
-   AWS SQS
-   AWS S3
-   Apache Camel
-   Jackson
-   Lombok
-   SLF4J Logger

------------------------------------------------------------------------

# 11. Current Gradle Configuration

Spring Boot:

``` text
4.1.1
```

Java:

``` text
21
```

Important dependencies include:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
implementation 'com.fasterxml.jackson.core:jackson-databind'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'io.awspring.cloud:spring-cloud-aws-starter-sqs'

implementation 'software.amazon.awssdk:s3'

implementation 'org.apache.camel.springboot:camel-spring-boot-starter'
implementation 'org.apache.camel.springboot:camel-aws2-sqs-starter'
implementation 'org.apache.camel.springboot:camel-jackson3-starter'
```

BOMs:

``` gradle
mavenBom "io.awspring.cloud:spring-cloud-aws-dependencies:4.1.0"
mavenBom "org.apache.camel.springboot:camel-spring-boot-bom:4.22.0"
```

## Dependency version rule

For AWS SDK S3, use:

``` gradle
implementation 'software.amazon.awssdk:s3'
```

Do NOT manually add an arbitrary version such as:

``` gradle
implementation 'software.amazon.awssdk:s3:2.20.0'
```

Dependency management should handle the version.

------------------------------------------------------------------------

# 12. Current application.yml

Current known configuration:

``` yaml
spring:
  application:
    name: validation_service

  mongodb:
    uri: ${MONGODB_URI}

  cloud:
    aws:
      region:
        static: ap-south-1
```

The IDE previously showed:

``` text
Unknown property 'spring.cloud'
```

This was treated as an IDE metadata warning rather than automatically
changing the configuration.

Do not randomly modify this configuration unless the build/runtime
demonstrates an actual problem.

The planned S3 configuration is:

``` yaml
aws:
  s3:
    bucket-name: book-platform-files-206465504931-ap-south-1-an
```

------------------------------------------------------------------------

# 13. DTOs

## FileUploadedEvent

Current:

``` java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadedEvent {

    private String eventType;
    private String requestId;
    private String bookId;
    private String authorId;
    private String fileFormat;
    private String s3Reference;
}
```

Important:

There is currently no confirmed ISBN field.

Do not add ISBN until its source/contract is confirmed.

------------------------------------------------------------------------

# 14. Validation DTOs

## ValidationErrorDto

``` java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidationErrorDto {

    private String code;
    private String message;
}
```

## ValidationResultDto

Conceptually contains:

``` java
private boolean passed;
private List<ValidationErrorDto> errors;
```

The object is used to collect validation errors.

------------------------------------------------------------------------

# 15. Validation Status

Current enum:

``` java
public enum ValidationStatus {
    NOT_STARTED,
    PROCESSING,
    SUCCESSFUL,
    FAILED
}
```

Current document:

``` java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Validation_Status")
public class ValidationStatusDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String requestId;

    private ValidationStatus isbn;
    private ValidationStatus fileExistence;
    private ValidationStatus fileExtension;
    private ValidationStatus fileSize;
    private ValidationStatus fileCorruption;
    private ValidationStatus fileName;
    private ValidationStatus requiredMetadata;
}
```

------------------------------------------------------------------------

# 16. ValidationStatusService

This service is already implemented.

Responsibilities:

-   Create a validation status record.
-   Initialize all seven validations as `NOT_STARTED`.
-   Update each validation independently.
-   Retrieve validation status by request ID.

The existing behavior includes:

``` text
createStatus(requestId)
```

and update methods for:

``` text
ISBN
File Existence
File Extension
File Size
File Corruption
File Name
Required Metadata
```

The `requestId` has a unique MongoDB index.

------------------------------------------------------------------------

# 17. Validators Currently Present

The project currently has these validator components:

``` text
IsbnValidator
FileExistenceValidator
FileExtensionValidator
FileSizeValidator
FileCorruptionValidator
FileNameValidator
RequiredMetadataValidator
```

------------------------------------------------------------------------

# 18. Current Validator Behavior

## ISBN Validator

Current intended logic:

-   Obtain ISBN.
-   Check that it is 13 digits.
-   Validate ISBN-13 check digit.

BUT:

The current `FileUploadedEvent` does not contain ISBN.

Therefore the final ISBN data source is still unresolved.

------------------------------------------------------------------------

## FileExistenceValidator

Currently checks whether the file is:

-   null
-   empty

This was originally designed for `MultipartFile`.

It will need to be refactored for the final S3-based architecture.

------------------------------------------------------------------------

## FileExtensionValidator

Currently accepts:

``` text
.pdf
.docx
.epub
```

Case-insensitive.

It checks the filename extension.

The implementation currently uses `MultipartFile`.

It will later need to work from the S3 object/reference rather than an
HTTP multipart request.

------------------------------------------------------------------------

## FileSizeValidator

The current implementation has a temporary maximum size.

Important:

> The final production/business maximum file size is NOT confirmed.

Earlier testing involved a smaller temporary limit, while the current
validator contains another temporary value.

Do not treat the current numeric limit as a confirmed business
requirement.

------------------------------------------------------------------------

## FileCorruptionValidator

Current behavior:

-   Reads the file stream.
-   Confirms that the content can be read.

This is not yet true format-specific corruption detection.

For example, simply reading an EPUB byte stream does not prove the EPUB
structure is valid.

This is an area for future improvement.

------------------------------------------------------------------------

## FileNameValidator

Currently checks that the filename exists.

The exact filename naming rule/regex has not been fully confirmed.

Do not invent a filename regex.

------------------------------------------------------------------------

## RequiredMetadataValidator

This was recently corrected.

Current method:

``` java
public ValidationResultDto validate(FileUploadedEvent event)
```

It validates required event fields:

``` text
requestId
bookId
authorId
fileFormat
eventType
s3Reference
```

It uses `isBlank()` checks.

If the event is null:

``` text
MISSING_METADATA
```

If individual fields are missing, corresponding validation errors are
returned.

This validator no longer requires `MultipartFile`.

------------------------------------------------------------------------

# 19. ValidationService

The current `ValidationService` contains the seven validation methods.

Conceptually:

``` text
validateISBN(...)
validateFileExistence(...)
validateFileExtension(...)
validateFileSize(...)
validateFileCorruption(...)
validateFileName(...)
validateRequiredMetadata(...)
```

There is also:

``` text
validateManuscript(...)
```

The orchestration currently:

1.  Creates validation status.
2.  Runs required metadata validation.
3.  Runs file existence.
4.  Runs extension.
5.  Runs size.
6.  Runs corruption.
7.  Runs filename.
8.  Runs ISBN.
9.  Collects all errors.
10. Returns a final `ValidationResultDto`.

Important:

> All validations should execute even if earlier validations fail.

This behavior is intentional.

------------------------------------------------------------------------

# 20. Legacy MultipartFile Flow

The original implementation used an HTTP endpoint:

``` text
Postman
   |
   v
POST /api/validate
   |
   v
MultipartFile + event JSON
   |
   v
ValidationController
   |
   v
ValidationService
```

Current controller:

``` java
@RestController
@RequestMapping("/api")
public class ValidationController {

    private final ValidationService validationService;
    private final ObjectMapper objectMapper;

    public ValidationController(
            ValidationService validationService,
            ObjectMapper objectMapper) {
        this.validationService = validationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
        value = "/validate",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ValidationResultDto> validateManuscript(
            @RequestPart("file") MultipartFile file,
            @RequestPart("event") String eventJson) throws Exception {

        FileUploadedEvent event =
                objectMapper.readValue(eventJson, FileUploadedEvent.class);

        ValidationResultDto result =
                validationService.validateManuscript(event, file);

        return ResponseEntity.ok(result);
    }
}
```

## Important architectural status

This is a **legacy/temporary HTTP testing flow**.

The final architecture should NOT depend on receiving the manuscript as
`MultipartFile`.

The final flow is:

``` text
SQS event
   |
   v
S3 reference
   |
   v
S3Service reads manuscript
```

Therefore, the validation core needs to be gradually refactored away
from `MultipartFile`.

Do not delete everything at once.

Refactor systematically.

------------------------------------------------------------------------

# 21. ObjectMapper Configuration

Current:

``` java
@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

The `ObjectMapper` is currently used by `ValidationProcessor` to
deserialize the incoming event.

------------------------------------------------------------------------

# 22. Activity Model

Current `Activity` DTO is:

``` java
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    private String state;
    private String applicationName;
    private String timestamp;
}
```

However, the latest actual Upload Service MongoDB document uses:

``` json
{
  "activityType": "REQUEST_CREATED",
  "applicationName": "upload-service",
  "timestamp": "..."
}
```

Therefore the DTO is currently stale compared with the latest actual
schema.

## Activity ID

There was a discussion about activity IDs.

The latest actual document does NOT show an activity ID.

Therefore:

> Do not invent an activity ID field until the actual contract is
> confirmed.

The Activity model should later be aligned with the real Upload Service
contract.

------------------------------------------------------------------------

# 23. BookEventDocument

Current older model:

``` java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "upload")
public class BookEventDocument {

    private String requestId;
    private String authorId;
    private String bookName;

    @JsonProperty("ISBN")
    private String isbn;

    private String eventType;
    private List<Activity> activities;
    private String s3Reference;
}
```

This is currently stale compared with the latest Upload Service
document.

Important differences:

-   Old model has `bookName`.
-   Latest contract uses `bookId`.
-   Old model includes ISBN.
-   Latest actual document does not currently show ISBN.

This model should be aligned later with the actual shared contract.

Do not make unrelated changes while working on the current S3 step.

------------------------------------------------------------------------

# 24. Repositories

## BookEventRepository

``` java
public interface BookEventRepository
        extends MongoRepository<BookEventDocument, String> {

    Optional<BookEventDocument> findByS3Reference(String s3Reference);
}
```

## ValidationStatusRepository

``` java
public interface ValidationStatusRepository
        extends MongoRepository<ValidationStatusDocument, String> {

    Optional<ValidationStatusDocument> findByRequestId(String requestId);
}
```

------------------------------------------------------------------------

# 25. Apache Camel Integration

Camel dependencies are already present.

Current route:

``` java
@Component
@ConditionalOnProperty(
    name = "validation.sqs.route.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ValidationRoute extends RouteBuilder {

    private final ValidationProcessor validationProcessor;

    public ValidationRoute(ValidationProcessor validationProcessor) {
        this.validationProcessor = validationProcessor;
    }

    @Override
    public void configure() {

        onException(Exception.class)
            .handled(false)
            .logStackTrace(true)
            .logExhausted(true);

        from("aws2-sqs:manuscript-validation-queue"
                + "?region=ap-south-1"
                + "&autoCreateQueue=false"
                + "&useDefaultCredentialsProvider=true")
            .routeId("validation-service-route")
            .log("Received message from validation queue")
            .process(validationProcessor);
    }
}
```

------------------------------------------------------------------------

# 26. ValidationProcessor

Current:

``` java
@Component
public class ValidationProcessor implements Processor {

    private static final Logger logger =
            LoggerFactory.getLogger(ValidationProcessor.class);

    private final ObjectMapper objectMapper;

    public ValidationProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String message =
                exchange.getIn().getBody(String.class);

        logger.info(
                "Received validation event: {}",
                message
        );

        FileUploadedEvent event =
                objectMapper.readValue(
                        message,
                        FileUploadedEvent.class
                );

        logger.info("Request ID: {}", event.getRequestId());
        logger.info("Book ID: {}", event.getBookId());
        logger.info("Author ID: {}", event.getAuthorId());
        logger.info("File Format: {}", event.getFileFormat());
        logger.info("S3 Reference: {}", event.getS3Reference());

        exchange.getIn().setBody(event);
    }
}
```

## Current status

The SQS → Camel → Processor flow has been tested and is working.

This means:

``` text
SQS
  ↓
Camel
  ↓
ValidationProcessor
  ↓
FileUploadedEvent
```

is already established.

Do not recreate the old native `@SqsListener`.

The old listener was deleted intentionally.

------------------------------------------------------------------------

# 27. Test Configuration

Because the Camel route attempts to connect to AWS, Spring Boot tests
were configured to disable it:

``` java
@SpringBootTest(properties = {
    "validation.sqs.route.enabled=false"
})
```

This allows:

``` text
.\gradlew.bat clean build
```

to run without requiring the SQS route to connect to AWS during the
application context test.

The production/runtime route remains enabled because:

``` java
matchIfMissing = true
```

------------------------------------------------------------------------

# 28. Build Verification

A previous build issue was encountered after changing the
RequiredMetadataValidator.

The problem was:

``` text
requiredMetadataValidator.validate(event, file)
```

while the validator had been changed to:

``` java
requiredMetadataValidator.validate(event)
```

The call was corrected.

After correction:

``` text
.\gradlew.bat clean build
```

worked successfully.

This is an important checkpoint.

------------------------------------------------------------------------

# 29. IDE / Gradle Issues Encountered

After adding/changing dependencies, VS Code showed errors such as:

``` text
The import org.apache.camel cannot be resolved
```

and:

``` text
Processor cannot be resolved
Exchange cannot be resolved
RouteBuilder cannot be resolved
```

Also:

``` text
The build file has been changed and may need reload to make it effective.
```

These were treated as likely Gradle/Java Language Server synchronization
issues when the actual Gradle build is correct.

Suggested recovery:

1.  Save `build.gradle`.
2.  Run:

``` powershell
.\gradlew.bat clean build
```

3.  If the build succeeds but VS Code still shows unresolved imports:
    -   run **Java: Clean Java Language Server Workspace**
    -   reload/reopen the project
    -   refresh Gradle dependencies.

Do not change correct dependencies merely because the IDE temporarily
shows stale errors.

------------------------------------------------------------------------

# 30. S3 Integration --- Current Next Step

The next implementation step is to make the Validation Service read the
existing manuscript from S3.

The purpose of S3Service is:

> READ / STREAM the uploaded manuscript.

It is NOT to upload validation results to S3.

------------------------------------------------------------------------

# 31. Planned S3Config

Create:

``` text
src/main/java/com/manuscriptvalidation/validation_service/config/S3Config.java
```

Planned implementation:

``` java
package com.manuscriptvalidation.validation_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder().build();
    }
}
```

This intentionally uses the AWS SDK default credential provider chain.

No access keys should be placed in the source code.

------------------------------------------------------------------------

# 32. Planned application.yml S3 Configuration

Add:

``` yaml
aws:
  s3:
    bucket-name: book-platform-files-206465504931-ap-south-1-an
```

The bucket name should be configurable rather than hardcoded directly
inside service logic.

------------------------------------------------------------------------

# 33. Planned S3Service

Create:

``` text
src/main/java/com/manuscriptvalidation/validation_service/service/S3Service.java
```

Initial read-oriented implementation:

``` java
package com.manuscriptvalidation.validation_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;

    public S3Service(
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {

        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public ResponseInputStream<GetObjectResponse> getObject(
            String s3Reference) {

        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Reference)
                        .build();

        return s3Client.getObject(request);
    }
}
```

The `s3Reference` is treated as the S3 object key.

Example:

``` text
injection/REQ-0003/lewis-lion-the-witch-and-the-wardrobe.epub
```

------------------------------------------------------------------------

# 34. Important Design Direction for S3 Validation

Do NOT solve the S3 integration by blindly doing:

``` text
S3 object
   ↓
download entire file
   ↓
convert to MultipartFile
   ↓
reuse old validators
```

That would preserve an HTTP-specific abstraction unnecessarily.

A better final direction is:

``` text
S3 object
   |
   +--> metadata
   |
   +--> object key / filename
   |
   +--> size
   |
   +--> content type
   |
   +--> input stream
```

Validators can then use the information they actually need.

For example:

``` text
File existence
    -> S3 object existence

Extension
    -> S3 key / filename

Size
    -> S3 object metadata

Filename
    -> S3 key / filename

Corruption
    -> S3 object stream / format parser

Required metadata
    -> FileUploadedEvent

ISBN
    -> pending contract confirmation
```

This is the preferred architectural direction.

------------------------------------------------------------------------

# 35. One-Step-at-a-Time Coding Plan

Do not implement the whole architecture in one change.

Continue in this order:

## Step 1 --- AWS S3 dependency

Confirm:

``` gradle
implementation 'software.amazon.awssdk:s3'
```

Then run:

``` powershell
.\gradlew.bat clean build
```

## Step 2 --- S3Config

Create `S3Config.java`.

Build again.

## Step 3 --- S3 configuration

Add bucket name to `application.yml`.

Build again.

## Step 4 --- S3Service

Create the read-oriented S3 service.

Build again.

## Step 5 --- Test S3 access

Use the known object:

``` text
injection/REQ-0003/lewis-lion-the-witch-and-the-wardrobe.epub
```

Confirm the application can retrieve/read it.

## Step 6 --- Refactor validation input

Move the validation core away from `MultipartFile`.

Do this carefully rather than replacing everything at once.

## Step 7 --- Refactor individual validators

Potentially:

``` text
FileExistenceValidator
FileExtensionValidator
FileSizeValidator
FileCorruptionValidator
FileNameValidator
```

to use S3/domain information.

Keep:

``` text
RequiredMetadataValidator
```

event-based.

ISBN implementation waits for the confirmed source.

## Step 8 --- Connect Processor to ValidationService

Change:

``` text
ValidationProcessor
    ↓
only logs event
```

into:

``` text
ValidationProcessor
    ↓
ValidationService
    ↓
S3Service
    ↓
Validators
```

## Step 9 --- Update validation status

Ensure every validation updates:

``` text
NOT_STARTED
PROCESSING
SUCCESSFUL / FAILED
```

## Step 10 --- Activities

Append validation activities to the existing request's `activities`
array.

Do not create unrelated separate activity documents.

First align the Activity model with the actual Upload Service contract.

## Step 11 --- Final PASS / FAIL

Determine overall outcome after all seven validations have executed.

## Step 12 --- Publish result event

Publish validation result to the next messaging flow as agreed.

## Step 13 --- SES

Integrate AWS SES for success/failure notification.

------------------------------------------------------------------------

# 36. Validation Execution Model

The final validation behavior should look like:

``` text
Request received
      |
      v
Create validation status
      |
      +-----------------------------+
      |                             |
      v                             v
Required metadata              File-related checks
                                      |
                  +-------------------+-------------------+
                  |        |          |        |          |
                  v        v          v        v          v
               Exists   Extension   Size   Corruption   Filename
                  |
                  +-------------------+
                           |
                           v
                         ISBN
                           |
                           v
                   Collect all results
                           |
                           v
                    Overall PASS/FAIL
```

Even if:

``` text
File extension = FAILED
```

the system should still run:

``` text
File size
File corruption
Filename
ISBN
etc.
```

------------------------------------------------------------------------

# 37. Error Handling

There is a `GlobalExceptionHandler`.

It contains standard exception handlers.

There is currently an unused import:

``` text
MissingServletRequestPartException
```

This is only a warning/cleanup item and is not a blocker.

Do not spend time on it while implementing the S3 flow unless needed.

------------------------------------------------------------------------

# 38. Logging Requirement

Use Logger rather than `System.out.println`.

Example:

``` java
private static final Logger logger =
        LoggerFactory.getLogger(ValidationProcessor.class);
```

Useful logs include:

``` text
Received validation event
Request ID
Book ID
Author ID
File format
S3 reference
Validation started
Validation completed
Validation failed
Final validation result
```

Do not log sensitive credentials.

------------------------------------------------------------------------

# 39. Java Concepts Being Practiced

The project is also being used to learn/practice:

## Dependency Injection

Example:

``` java
public ValidationService(
        IsbnValidator isbnValidator,
        FileExistenceValidator fileExistenceValidator,
        ...
) {
    ...
}
```

## Interfaces

Repositories and Camel `Processor`.

## Java Streams

Potentially useful when processing validation results and activities.

## Predicate

Potentially useful for reusable validation conditions.

## Processor

Camel's:

``` java
Processor
```

is used to process incoming messages.

## Route

Camel's:

``` java
RouteBuilder
```

defines:

``` text
SQS -> Processor
```

## Service

Business logic is kept in services.

## Repository

MongoDB access is kept in repositories.

------------------------------------------------------------------------

# 40. Why Apache Camel Is Used

Camel is being used as the integration/routing layer.

Current route:

``` text
aws2-sqs
   |
   v
ValidationProcessor
```

This provides a clean place to later add:

-   logging
-   transformations
-   error handling
-   routing
-   retries
-   downstream event publishing

The project should continue using Camel rather than introducing another
competing listener mechanism.

------------------------------------------------------------------------

# 41. Old SQS Listener Decision

A native Spring AWS SQS listener existed earlier.

It was removed.

Do NOT recreate:

``` java
@SqsListener(...)
```

The final project uses:

``` text
Camel aws2-sqs route
```

for SQS consumption.

------------------------------------------------------------------------

# 42. Current State --- What Is Already Done

### Completed

-   [x] Spring Boot project created.
-   [x] Gradle setup.
-   [x] Java 21 configuration.
-   [x] MongoDB connection.
-   [x] MongoDB validation collection.
-   [x] Validation status document.
-   [x] Validation status repository.
-   [x] Validation status service.
-   [x] Unique request ID handling.
-   [x] Seven validator classes.
-   [x] Validation result DTO.
-   [x] Validation error DTO.
-   [x] Required metadata validator corrected to current event fields.
-   [x] Legacy controller exists for temporary testing.
-   [x] AWS SSO setup.
-   [x] AWS CLI authentication.
-   [x] SQS queue setup.
-   [x] Camel dependencies.
-   [x] Camel route.
-   [x] Camel processor.
-   [x] SQS -\> Camel -\> Processor tested.
-   [x] Old `@SqsListener` removed.
-   [x] Spring tests configured to disable the live SQS route.
-   [x] Successful `clean build` after the RequiredMetadataValidator
    fix.
-   [x] Actual S3 object identified.
-   [x] Final S3 -\> SNS -\> SQS architecture decision established.

------------------------------------------------------------------------

# 43. Current State --- What Is NOT Finished

### Pending

-   [ ] Confirm final AWS S3 dependency is present.
-   [ ] Create S3Config.
-   [ ] Add S3 bucket configuration.
-   [ ] Create S3Service.
-   [ ] Test S3 object retrieval.
-   [ ] Refactor validators away from MultipartFile.
-   [ ] Connect ValidationProcessor to ValidationService.
-   [ ] Finalize ISBN source.
-   [ ] Finalize production file-size limit.
-   [ ] Finalize filename rule.
-   [ ] Improve true corruption validation.
-   [ ] Align Activity DTO with actual Upload Service schema.
-   [ ] Implement activity updates.
-   [ ] Implement final PASS/FAIL event publishing.
-   [ ] Implement AWS SES notification.
-   [ ] Complete end-to-end integration testing.

------------------------------------------------------------------------

# 44. Known Unconfirmed Requirements

These must not be guessed.

## ISBN source

Status:

``` text
PENDING
```

Question:

``` text
Where does ISBN come from?
```

Possible sources exist, but none should be assumed until the Upload
Service contract confirms it.

------------------------------------------------------------------------

## Maximum file size

Status:

``` text
PENDING
```

Current code contains a temporary limit.

Do not call it the final business requirement.

------------------------------------------------------------------------

## Filename rule

Status:

``` text
PENDING / PARTIALLY DEFINED
```

Current validator only performs a basic filename check.

Do not invent a regex.

------------------------------------------------------------------------

## Corruption validation

Status:

``` text
BASIC IMPLEMENTATION
```

Reading the stream is not the same as validating the internal structure
of PDF/DOCX/EPUB.

------------------------------------------------------------------------

## Activity schema

Status:

``` text
NEEDS ALIGNMENT
```

Latest actual data uses:

``` text
activityType
applicationName
timestamp
```

Current DTO uses:

``` text
state
applicationName
timestamp
```

Resolve against the shared contract before final implementation.

------------------------------------------------------------------------

# 45. Important Things NOT to Do

## Do not change architecture to:

``` text
S3 -> SQS
```

Final architecture is:

``` text
S3 -> SNS -> SQS
```

## Do not use hardcoded AWS credentials

Do not add:

``` text
accessKey
secretKey
```

to Java source or configuration.

Use AWS SSO/default credential provider.

## Do not upload validation results to S3

S3Service is intended to:

``` text
READ manuscript
```

not:

``` text
UPLOAD validation result
```

## Do not recreate @SqsListener

Camel is the selected SQS integration.

## Do not make every validation depend on MultipartFile

The final system receives a reference to an object in S3.

## Do not stop after the first failed validation

All validations must execute.

## Do not invent missing business requirements

Especially:

-   ISBN source
-   file-size limit
-   filename regex
-   activity ID
-   activity schema

------------------------------------------------------------------------

# 46. Recommended Final Internal Flow

A good final implementation should look approximately like:

``` text
ValidationProcessor
        |
        | FileUploadedEvent
        v
ValidationService
        |
        +--------------------------+
        |                          |
        v                          v
ValidationStatusService         S3Service
                                   |
                                   v
                              S3 object
                                   |
                  +----------------+----------------+
                  |                |                |
                  v                v                v
             Metadata          Filename          Stream
                  |                |                |
                  +----------------+----------------+
                                   |
                                   v
                              Validators
                                   |
                                   v
                            ValidationResultDto
                                   |
                    +--------------+--------------+
                    |                             |
                    v                             v
             MongoDB status                 Activity update
                    |
                    v
                 PASS/FAIL
                    |
                    v
              Publish result
                    |
                    v
                   SES
```

------------------------------------------------------------------------

# 47. Coding Discipline for Future Work

When continuing development:

### Rule 1

First inspect the current code.

### Rule 2

Make one logical change at a time.

### Rule 3

Run:

``` powershell
.\gradlew.bat clean build
```

after significant changes.

### Rule 4

If the build passes but VS Code has red import errors, refresh the
Java/Gradle workspace before rewriting working code.

### Rule 5

Do not refactor unrelated classes while solving one issue.

### Rule 6

Explain why a change is required.

### Rule 7

Prefer constructor injection.

### Rule 8

Use Logger.

### Rule 9

Keep business validation in validators/services, not in Camel route
configuration.

### Rule 10

Keep AWS infrastructure concerns in dedicated configuration/services.

------------------------------------------------------------------------

# 48. Current Immediate Next Action

The project should continue from the **S3 integration step**.

The immediate sequence is:

``` text
1. Verify/add S3 dependency
        ↓
2. Build
        ↓
3. Create S3Config
        ↓
4. Build
        ↓
5. Add bucket configuration
        ↓
6. Build
        ↓
7. Create S3Service
        ↓
8. Build
        ↓
9. Test actual S3 object retrieval
```

Known test object:

``` text
injection/REQ-0003/lewis-lion-the-witch-and-the-wardrobe.epub
```

Only after S3 retrieval works should the validation layer be refactored
from `MultipartFile` to S3-based input.

------------------------------------------------------------------------

# 49. Quick Reference

## Project

``` text
Manuscript Validation
```

## Service

``` text
validation_service
```

## Java

``` text
21
```

## Spring Boot

``` text
4.1.1
```

## AWS Region

``` text
ap-south-1
```

## AWS SSO Profile

``` text
manuscript-dev
```

## S3 Bucket

``` text
book-platform-files-206465504931-ap-south-1-an
```

## SQS Queue

``` text
manuscript-validation-queue
```

## MongoDB Database

``` text
book_management
```

## MongoDB Validation Collection

``` text
Validation_Status
```

## Messaging

``` text
S3 -> SNS -> SQS
```

## SQS consumer

``` text
Apache Camel
```

## Main processor

``` text
ValidationProcessor
```

## Main business service

``` text
ValidationService
```

## S3 service

``` text
S3Service
```

## Validators

``` text
IsbnValidator
FileExistenceValidator
FileExtensionValidator
FileSizeValidator
FileCorruptionValidator
FileNameValidator
RequiredMetadataValidator
```

## Validation statuses

``` text
NOT_STARTED
PROCESSING
SUCCESSFUL
FAILED
```

------------------------------------------------------------------------

# 50. Final Project Mentor Context

The project is no longer at the "start from scratch" stage.

The following foundation is already established:

``` text
Spring Boot
MongoDB
Validation architecture
AWS authentication
SQS
Camel
ValidationProcessor
ValidationRoute
Validation status tracking
```

The current implementation phase is:

``` text
S3 integration
        ↓
S3-based validation
        ↓
ValidationService integration
        ↓
Activity updates
        ↓
PASS/FAIL event
        ↓
SES
        ↓
End-to-end testing
```

The most important principle for future sessions is:

> Continue from the current implementation state. Do not restart the
> project or redesign already-decided architecture unless the user
> explicitly changes the requirement.

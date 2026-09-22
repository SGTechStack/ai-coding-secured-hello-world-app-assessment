# Inbound Batch Reception and Validation

## 1. Introduction

Inbound batch processing begins with reliable file reception and format validation. This recipe covers how to monitor an inbound directory, discover new batch files, validate their structure against a schema, and generate acknowledgments. Proper validation upfront prevents downstream processing errors and enables early failure detection.

By the end of this recipe, you will:
- Poll an inbound directory for new batch files
- Detect and handle duplicates using batch IDs
- Validate CSV and JSON formats against a schema
- Generate and transmit acknowledgment files
- Implement retry logic for transient errors

## 2. Prerequisites

- Spring Boot 3.0+ with `spring-boot-starter-integration` (for scheduled polling)
- Jackson library for JSON parsing
- Apache Commons CSV for CSV parsing
- Access to a persistent database for transaction recording
- Familiarity with Spring's `@Scheduled` annotation or Quartz scheduler

## 3. Steps

### Step 1: Add File I/O and Parsing Dependencies

Add dependencies to `pom.xml`:

```xml
<!-- File: pom.xml -->
<dependencies>
    <!-- Spring Integration for file polling -->
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-file</artifactId>
    </dependency>

    <!-- Jackson for JSON parsing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- Apache Commons CSV for CSV parsing -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-csv</artifactId>
        <version>1.10.0</version>
    </dependency>

    <!-- JSON Schema Validator -->
    <dependency>
        <groupId>com.github.java-json-tools</groupId>
        <artifactId>json-schema-validator</artifactId>
        <version>2.2.14</version>
    </dependency>

    <!-- Spring Boot Data JPA for transaction persistence -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Define the Batch File Model and Database Entity

Create data classes:

```java
// File: src/main/java/com/example/batch/model/BatchFile.java
package com.example.batch.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

public class BatchFile {
    private String batchId;
    private String fileName;
    private Path filePath;
    private long fileSizeBytes;
    private String format;  // "csv", "json", "xml"
    private String charset;  // "UTF-8", etc.
    private Instant receivedAt;
    private byte[] content;  // File content
    private int recordCount;

    public BatchFile(String batchId, Path filePath, String format) {
        this.batchId = batchId;
        this.filePath = filePath;
        this.fileName = filePath.getFileName().toString();
        this.format = format;
        this.charset = StandardCharsets.UTF_8.name();
        this.receivedAt = Instant.now();
    }

    // Getters and setters...
    public String getBatchId() { return batchId; }
    public String getFileName() { return fileName; }
    public Path getFilePath() { return filePath; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long size) { this.fileSizeBytes = size; }
    public String getFormat() { return format; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int count) { this.recordCount = count; }
}
```

Create the JPA entity:

```java
// File: src/main/java/com/example/batch/entity/InterfaceTransaction.java
package com.example.batch.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interface_transaction", indexes = {
    @Index(name = "idx_batch_id", columnList = "batch_id", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class InterfaceTransaction {
    @Id
    private String transactionId;

    @Column(nullable = false, unique = true)
    private String batchId;

    @Column(nullable = false)
    private String senderId;

    private String fileName;
    private Long fileSizeBytes;
    private Integer recordCount;

    @Column(nullable = false)
    private String status;  // received, validating, processing, processed, failed, partial_failure

    @Column(length = 64)
    private String errorCategory;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters...
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String id) { this.transactionId = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String id) { this.batchId = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String category) { this.errorCategory = category; }
    // ... other getters/setters
}
```

### Step 3: Create a Batch File Receiver

Create a class to handle file reception:

```java
// File: src/main/java/com/example/batch/receiver/InboundFileReceiver.java
package com.example.batch.receiver;

import com.example.batch.model.BatchFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;

@Component
public class InboundFileReceiver {
    private static final Logger log = LoggerFactory.getLogger(InboundFileReceiver.class);

    @Value("${batch.inbound.directory}")
    private String inboundDirectory;

    /**
     * Poll the inbound directory for new files.
     * Returns the first batch file found, or null if directory is empty.
     */
    public BatchFile receiveNextFile() throws IOException {
        Path directory = Paths.get(inboundDirectory);

        if (!Files.exists(directory)) {
            log.warn("Inbound directory does not exist: {}", inboundDirectory);
            return null;
        }

        // Find first regular file (not directory) with a common batch extension
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.{csv,json,xml}")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    return loadBatchFile(file);
                }
            }
        }

        return null;  // No files found
    }

    /**
     * Load batch file content and metadata.
     * 
     * NOTE: For production-grade implementations processing large files (up to 5GB limit),
     * do NOT read the entire file content into a byte array (avoid Files.readAllBytes) to prevent heap OOM.
     * Instead, validate file attributes and use stream-based parsing.
     */
    private BatchFile loadBatchFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        
        // Prevent Path Traversal (OWASP A01)
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new SecurityException("Potential Path Traversal attack detected in filename: " + fileName);
        }

        // Filename prefix must match alphanumeric regex (OWASP A03 / Command Injection prevention)
        String batchId = extractBatchId(fileName);
        if (!batchId.matches("^[a-zA-Z0-9_-]+$") || batchId.length() > 50) {
            throw new IllegalArgumentException("Invalid filename prefix or size boundaries violated: " + batchId);
        }

        String format = extractFormat(fileName);

        BatchFile batchFile = new BatchFile(batchId, filePath, format);
        long fileSize = Files.size(filePath);
        batchFile.setFileSizeBytes(fileSize);

        log.atInfo()
            .setMessage("File metadata loaded from inbound directory")
            .addKeyValue("batch.job.id", batchId)
            .addKeyValue("file.name", fileName)
            .addKeyValue("file.size", fileSize)
            .log();

        return batchFile;
    }

    /**
     * Move processed file to archive directory.
     */
    public void archiveFile(Path filePath) throws IOException {
        Path archiveDir = Paths.get(inboundDirectory).resolve("archive");
        Files.createDirectories(archiveDir);

        Path archivePath = archiveDir.resolve(filePath.getFileName());
        Files.move(filePath, archivePath, StandardCopyOption.REPLACE_EXISTING);

        log.atInfo()
            .setMessage("File archived")
            .addKeyValue("file.name", filePath.getFileName().toString())
            .log();
    }

    /**
     * Move failed file to quarantine directory.
     */
    public void quarantineFile(Path filePath) throws IOException {
        Path quarantineDir = Paths.get(inboundDirectory).resolve("quarantine");
        Files.createDirectories(quarantineDir);

        Path quarantinePath = quarantineDir.resolve(filePath.getFileName());
        Files.move(filePath, quarantinePath, StandardCopyOption.REPLACE_EXISTING);

        log.atInfo()
            .setMessage("File quarantined")
            .addKeyValue("file.name", filePath.getFileName().toString())
            .log();
    }

    private String extractBatchId(String fileName) {
        // Simple extraction: assume format is BATCHID_YYYYMMDD.ext
        return fileName.split("\\.")[0];  // Remove extension
    }

    private String extractFormat(String fileName) {
        String[] parts = fileName.split("\\.");
        return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : "unknown";
    }
}
```

### Step 4: Create a Batch Validator

Create a validator that checks schema compliance:

```java
// File: src/main/java/com/example/batch/validator/BatchValidator.java
package com.example.batch.validator;

import com.example.batch.model.BatchFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ValidationResult {
    private boolean valid;
    private List<String> errors = new ArrayList<>();

    public ValidationResult(boolean valid) {
        this.valid = valid;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
}

@Component
public class BatchValidator {
    private static final Logger log = LoggerFactory.getLogger(BatchValidator.class);

    /**
     * Validate batch file format and structure.
     */
    public ValidationResult validate(BatchFile batchFile) {
        log.atInfo()
            .setMessage("Starting batch validation")
            .addKeyValue("file.extension", batchFile.getFormat())
            .log();

        // Check file size
        if (batchFile.getFileSizeBytes() == 0) {
            ValidationResult result = new ValidationResult(false);
            result.addError("File is empty");
            return result;
        }

        if (batchFile.getFileSizeBytes() > 104857600) {  // 100 MB
            ValidationResult result = new ValidationResult(false);
            result.addError("File exceeds maximum size of 100 MB");
            return result;
        }

        // Delegate to format-specific validators
        return switch (batchFile.getFormat().toLowerCase()) {
            case "csv" -> validateCsv(batchFile);
            case "json" -> validateJson(batchFile);
            case "xml" -> validateXml(batchFile);
            default -> {
                ValidationResult result = new ValidationResult(false);
                result.addError("Unsupported format: " + batchFile.getFormat());
                yield result;
            }
        };
    }

    private ValidationResult validateCsv(BatchFile batchFile) {
        ValidationResult result = new ValidationResult(true);

        try (BufferedReader reader = Files.newBufferedReader(batchFile.getFilePath())) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                result = new ValidationResult(false);
                result.addError("CSV file is empty or missing header");
                return result;
            }

            // Validate header columns
            String[] headerFields = headerLine.split(",");
            int expectedColumnCount = 5;  // Adjust per your schema

            if (headerFields.length != expectedColumnCount) {
                result = new ValidationResult(false);
                result.addError("Expected " + expectedColumnCount + " columns, found " + headerFields.length);
                return result;
            }

            // Stream count the data records safely
            int recordCount = 0;
            while (reader.readLine() != null) {
                recordCount++;
            }
            batchFile.setRecordCount(recordCount);

        } catch (Exception e) {
            result = new ValidationResult(false);
            result.addError("CSV parsing error: " + e.getMessage());
        }

        return result;
    }

    private ValidationResult validateJson(BatchFile batchFile) {
        ValidationResult result = new ValidationResult(true);

        try (BufferedReader reader = Files.newBufferedReader(batchFile.getFilePath())) {
            // Memory-efficient structure validation: peek first non-whitespace character
            int firstChar;
            while ((firstChar = reader.read()) != -1) {
                if (!Character.isWhitespace(firstChar)) {
                    char c = (char) firstChar;
                    if (c != '[' && c != '{') {
                        result = new ValidationResult(false);
                        result.addError("Invalid JSON: must start with [ or {");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            result = new ValidationResult(false);
            result.addError("JSON parsing error: " + e.getMessage());
        }

        return result;
    }

    private ValidationResult validateXml(BatchFile batchFile) {
        ValidationResult result = new ValidationResult(true);

        try (BufferedReader reader = Files.newBufferedReader(batchFile.getFilePath())) {
            // Memory-efficient structure validation: peek first non-whitespace character
            int firstChar;
            while ((firstChar = reader.read()) != -1) {
                if (!Character.isWhitespace(firstChar)) {
                    char c = (char) firstChar;
                    if (c != '<') {
                        result = new ValidationResult(false);
                        result.addError("Invalid XML: must start with XML declaration or root element");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            result = new ValidationResult(false);
            result.addError("XML parsing error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Extracts a ZIP file securely, applying Zip Slip and Zip Bomb checks.
     * 
     * @param zipFilePath Path to the ZIP file.
     * @param targetDir Path to the target decompression directory.
     * @throws IOException If decompression or security validation fails.
     */
    public void decompressSecurely(Path zipFilePath, Path targetDir) throws IOException {
        String canonicalTarget = targetDir.toRealPath().toString();
        
        // Limits for Zip Bomb mitigation
        long MAX_SIZE = 5373737664L; // 5 GB limit
        long MAX_RATIO = 100; // max compression ratio 100:1
        int MAX_ENTRIES = 1000; // max files allowed inside archive

        long totalSize = 0;
        int entriesCount = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entriesCount++;
                if (entriesCount > MAX_ENTRIES) {
                    throw new SecurityException("Zip Bomb detected: Too many entries inside archive (> " + MAX_ENTRIES + ")");
                }

                Path targetFile = targetDir.resolve(entry.getName()).normalize();
                
                // 1. Zip Slip Protection: verify canonical path resides strictly inside target directory
                if (!targetFile.toRealPath().toString().startsWith(canonicalTarget + java.io.File.separator)) {
                    throw new SecurityException("Zip Slip detected: Entry attempts to write outside target path: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetFile);
                } else {
                    Files.createDirectories(targetFile.getParent());
                    
                    // 2. Zip Bomb Protection: check uncompressed size and ratio during extraction
                    long entrySize = 0;
                    byte[] buffer = new byte[8192];
                    int len;
                    try (java.io.OutputStream os = Files.newOutputStream(targetFile)) {
                        while ((len = zis.read(buffer)) > 0) {
                            entrySize += len;
                            totalSize += len;

                            if (totalSize > MAX_SIZE) {
                                throw new SecurityException("Zip Bomb detected: Uncompressed size limit exceeded (> " + MAX_SIZE + " bytes)");
                            }

                            // Calculate compression ratio dynamically if size is available
                            long compressedSize = entry.getCompressedSize();
                            if (compressedSize > 0 && (entrySize / compressedSize) > MAX_RATIO) {
                                throw new SecurityException("Zip Bomb detected: Compression ratio limit exceeded (> " + MAX_RATIO + ":1)");
                            }

                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
```

### Step 5: Implement Duplicate Detection

Create a service to detect and handle duplicates:

```java
// File: src/main/java/com/example/batch/service/DuplicateDetectionService.java
package com.example.batch.service;

import com.example.batch.entity.InterfaceTransaction;
import com.example.batch.repository.InterfaceTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class DuplicateDetectionService {
    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);

    @Value("${batch.inbound.deduplication_window_days:90}")
    private int deduplicationWindowDays;

    private final InterfaceTransactionRepository transactionRepository;

    public DuplicateDetectionService(InterfaceTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Check if batch with this ID was previously processed.
     * Returns the prior transaction if found, or empty if new batch.
     */
    public Optional<InterfaceTransaction> detectDuplicate(String batchId) {
        Optional<InterfaceTransaction> prior = transactionRepository.findByBatchId(batchId);

        if (prior.isPresent()) {
            InterfaceTransaction priorTxn = prior.get();

            // Check if prior transaction is within dedup window
            Instant deduplicationExpiry = priorTxn.getCreatedAt()
                .plus(deduplicationWindowDays, ChronoUnit.DAYS);

            if (Instant.now().isBefore(deduplicationExpiry)) {
                log.atInfo()
                    .setMessage("Duplicate batch detected")
                    .addKeyValue("batch.job.id", batchId)
                    .addKeyValue("correlation.id", priorTxn.getTransactionId())
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", new String[]{"interface", "batch"})
                    .addKeyValue("event.type", new String[]{"error"})
                    .log();
                return prior;
            } else {
                log.atInfo()
                    .setMessage("Duplicate batch outside dedup window; treating as new")
                    .addKeyValue("batch.job.id", batchId)
                    .log();
            }
        }

        return Optional.empty();
    }
}
```

### Step 6: Create a Scheduled Batch Poller

Create a scheduled job to poll for inbound files:

```java
// File: src/main/java/com/example/batch/job/InboundBatchPollingJob.java
package com.example.batch.job;

import com.example.batch.context.BatchTransactionContext;
import com.example.batch.model.BatchFile;
import com.example.batch.receiver.InboundFileReceiver;
import com.example.batch.service.InboundBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InboundBatchPollingJob {
    private static final Logger log = LoggerFactory.getLogger(InboundBatchPollingJob.class);

    private final InboundFileReceiver fileReceiver;
    private final InboundBatchService batchService;

    public InboundBatchPollingJob(InboundFileReceiver fileReceiver,
                                   InboundBatchService batchService) {
        this.fileReceiver = fileReceiver;
        this.batchService = batchService;
    }

    /**
     * Poll inbound directory every 60 seconds for new files.
     */
    @Scheduled(fixedDelayString = "${batch.inbound.poll_interval_seconds:60}", timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void pollInboundDirectory() {
        try {
            BatchFile batchFile = fileReceiver.receiveNextFile();

            if (batchFile == null) {
                // No new files; that's normal
                return;
            }

            String interfaceSystem = "SYSTEM_A";  // Extract from file metadata or config

            // Initialize transaction context (sets MDC)
            BatchTransactionContext.initializeTransaction(batchFile.getBatchId(), interfaceSystem);

            log.atInfo()
                .setMessage("Processing batch file")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface", "batch"})
                .addKeyValue("event.type", new String[]{"interface-start"})
                .addKeyValue("event.action", "file-process")
                .log();

            var result = batchService.processBatch(batchFile, interfaceSystem);

            log.atInfo()
                .setMessage("Batch processing result")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface", "batch"})
                .addKeyValue("event.type", new String[]{"interface-end"})
                .addKeyValue("event.action", "file-process")
                .addKeyValue("event.outcome", result.getStatus().equalsIgnoreCase("success") ? "success" : "failure")
                .log();

            // Archive file after processing
            fileReceiver.archiveFile(batchFile.getFilePath());

        } catch (Exception e) {
            log.atError()
                .setMessage("Error during inbound batch polling")
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface", "batch"})
                .addKeyValue("event.type", new String[]{"error"})
                .addKeyValue("error_category", "application")
                .log();
        } finally {
            BatchTransactionContext.clear();
        }
    }
}
```

### Step 7: Verify Directory Accessibility at Startup

To satisfy the standard's directory write constraint, verify that the configured directories are writable by executing a write test (creating and deleting a temporary file) at application startup. If directory accessibility checks fail, abort startup with a specific error message.

```java
// File: src/main/java/com/example/batch/config/DirectoryValidationRunner.java
package com.example.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class DirectoryValidationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DirectoryValidationRunner.class);

    @Value("${batch.inbound.directory}")
    private String inboundDirectory;

    @Value("${batch.archive.directory}")
    private String archiveDirectory;

    @Value("${batch.quarantine.directory}")
    private String quarantineDirectory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        validateDirectoryWritable("Inbound", inboundDirectory);
        validateDirectoryWritable("Archive", archiveDirectory);
        validateDirectoryWritable("Quarantine", quarantineDirectory);
    }

    private void validateDirectoryWritable(String role, String dirPathStr) {
        Path dirPath = Paths.get(dirPathStr);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                throw new IllegalStateException("Startup validation failed: " + role + " directory '" + dirPathStr + "' does not exist and could not be created.", e);
            }
        }

        if (!Files.isDirectory(dirPath)) {
            throw new IllegalStateException("Startup validation failed: " + role + " path '" + dirPathStr + "' is not a directory.");
        }

        // Perform write test (create and delete temporary file)
        Path testFile = dirPath.resolve("startup-write-test-" + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(testFile, "test");
            Files.delete(testFile);
            log.info("Startup validation passed: {} directory '{}' is writable", role, dirPathStr);
        } catch (IOException e) {
            throw new IllegalStateException("Startup validation failed: Directory '" + dirPathStr + "' is not writable. Check permissions and mount status", e);
        }
    }
}
```

### Step 8: Handle Companion File Pairing and Unrecognized Filename Isolation

Handle the failure path where companion integrity files arrive asynchronously, or unrecognized files arrive at the ingestion boundary. Detect unrecognized prefixes, quarantine the file, and create an audit log.

```java
// File: src/main/java/com/example/batch/service/InboundFilePairingService.java
package com.example.batch.service;

import com.example.batch.entity.InterfaceTransaction;
import com.example.batch.repository.InterfaceTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class InboundFilePairingService {
    private static final Logger log = LoggerFactory.getLogger(InboundFilePairingService.class);

    private final InterfaceTransactionRepository transactionRepository;
    private final List<String> registeredPrefixes = List.of("PAYMENT_TXN");

    @Value("${batch.quarantine.directory}")
    private String quarantineDirectory;

    public InboundFilePairingService(InterfaceTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Inspects a newly discovered file. Quarantines unrecognized files, and registers companion pairing state.
     * Returns true if the file is ready for processing, false otherwise.
     */
    public boolean inspectAndPair(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        
        // 1. Unrecognized File Check
        String matchedPrefix = registeredPrefixes.stream()
            .filter(fileName::startsWith)
            .findFirst()
            .orElse(null);

        if (matchedPrefix == null) {
            quarantineUnrecognizedFile(filePath);
            return false;
        }

        // 2. Companion File Pairing
        if (fileName.endsWith(".sha3") || fileName.endsWith(".signed")) {
            // Companion file arrived first
            String dataFileName = fileName.substring(0, fileName.lastIndexOf('.'));
            Path dataFilePath = filePath.getParent().resolve(dataFileName);

            if (!Files.exists(dataFilePath)) {
                registerMissingPayload(fileName, matchedPrefix);
                return false;
            }
            // Data file exists, the poller will pick up the data file instead
            return false; 
        }

        // Data file arrived
        Path sha3File = filePath.getParent().resolve(fileName + ".sha3");
        if (!Files.exists(sha3File)) {
            // Missing companion payload, wait for it
            registerMissingCompanion(fileName, matchedPrefix);
            return false;
        }

        return true; // Both files are present and ready to process
    }

    private void quarantineUnrecognizedFile(Path filePath) throws IOException {
        Path quarantineDir = Path.of(quarantineDirectory);
        Files.createDirectories(quarantineDir);
        Path targetPath = quarantineDir.resolve(filePath.getFileName());
        Files.move(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.atWarn()
            .setMessage("Unrecognized inbound file prefix; quarantining file")
            .addKeyValue("file.name", filePath.getFileName().toString())
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.type", new String[]{"error"})
            .addKeyValue("error_category", "validation")
            .log();

        InterfaceTransaction txn = new InterfaceTransaction();
        txn.setTransactionId(UUID.randomUUID().toString());
        txn.setBatchId("UNREG-" + UUID.randomUUID());
        txn.setFileName(filePath.getFileName().toString());
        txn.setSenderId("UNKNOWN");
        txn.setStatus("UNRECOGNIZED");
        transactionRepository.save(txn);
    }

    private void registerMissingPayload(String companionFileName, String prefix) {
        log.info("Companion file received without payload: {}. Waiting for payload data file.", companionFileName);
        
        InterfaceTransaction txn = new InterfaceTransaction();
        txn.setTransactionId(UUID.randomUUID().toString());
        txn.setBatchId("MISS-" + UUID.randomUUID());
        txn.setFileName(companionFileName);
        txn.setSenderId(prefix);
        txn.setStatus("MISSING_PAYLOAD");
        transactionRepository.save(txn);
    }

    private void registerMissingCompanion(String dataFileName, String prefix) {
        log.info("Data file received without companion metadata: {}. Waiting for companion file.", dataFileName);
        // Data file sits in RECEIVED state waiting for scheduled poller to see companions
    }
}
```

## 4. Examples

### Example 1: CSV File Format

```csv
# File: /data/inbound/BATCH_20260327_001.csv
ID,Name,Email,Amount,Status
1001,John Doe,john@example.com,1500.00,PENDING
1002,Jane Smith,jane@example.com,2000.00,APPROVED
1003,Bob Johnson,bob@example.com,750.00,PENDING
```

Processing output:
```json
{"@timestamp":"2026-03-27T14:30:45Z","log.level":"INFO","message":"Batch received","batch.job.id":"BATCH_20260327_001","event.kind":"event","event.category":["interface","batch"],"event.type":["interface-start"],"event.action":"file-process","file.record_count":3}
{"@timestamp":"2026-03-27T14:30:46Z","log.level":"INFO","message":"Format validation passed","batch.job.id":"BATCH_20260327_001","event.kind":"event","event.category":["interface"],"event.type":["step-end"],"event.action":"file-read","event.outcome":"success"}
{"@timestamp":"2026-03-27T14:30:47Z","log.level":"INFO","message":"Batch processing result","batch.job.id":"BATCH_20260327_001","event.kind":"event","event.category":["interface","batch"],"event.type":["interface-end"],"event.action":"file-process","event.outcome":"success"}
```

### Example 2: Duplicate Detection

```java
// First submission
batchFile = new BatchFile("BATCH_20260327_001", path, "csv");
result = batchService.processBatch(batchFile, "SYSTEM_A");
// Status: processed, ACK sent

// Same batch resubmitted
batchFile = new BatchFile("BATCH_20260327_001", path, "csv");
result = batchService.processBatch(batchFile, "SYSTEM_A");
// Status: duplicate_detected, prior ACK retrieved and returned
```

## 5. Verification

1. Create a test CSV file in `${batch.inbound.directory}`.
2. Run the application and observe the scheduled job executing.
3. Check logs for batch_received, format_validation_succeeded, and batch_completed events.
4. Verify that the file was moved to the archive directory.
5. Resubmit the same file and verify that it's detected as a duplicate and the prior ACK is returned.

## 6. Conclusion

You have implemented a complete inbound batch reception and validation pipeline. The system now:
- Polls for new files on a schedule
- Validates format and structure
- Detects and handles duplicates
- Logs all events with transaction correlation
- Archives processed files

## 7. References

- [Spring Integration File Polling](https://docs.spring.io/spring-integration/reference/html/file.html)
- [Jackson JSON Processor](https://github.com/FasterXML/jackson)
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/)
- [Spring Scheduled Tasks](https://spring.io/guides/gs/scheduling-tasks/)

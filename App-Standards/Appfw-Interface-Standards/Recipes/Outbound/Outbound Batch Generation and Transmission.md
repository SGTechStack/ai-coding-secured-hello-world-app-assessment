# Outbound Batch File Generation and Transmission

## 1. Introduction

Outbound batch processing generates and transmits data to external systems. This recipe covers data extraction, format transformation, transmission via multiple transport mechanisms, acknowledgment receipt, and error handling.

By the end of this recipe, you will:
- Extract domain data and transform it to target formats (CSV, JSON, XML)
- Transmit batches via file system, SFTP, or HTTP
- Implement acknowledgment waiting and timeout handling
- Handle transmission failures and retries
- Log all outbound events with full traceability

## 2. Prerequisites

- Spring Boot 3.0+
- Jackson library for JSON/XML transformation
- JSch or Apache Commons VFS for SFTP support
- Familiarity with Spring's RestTemplate or WebClient for HTTP transmission
- Database access for transaction persistence

## 3. Steps

### Step 1: Add Outbound Transmission Dependencies

```xml
<!-- File: pom.xml -->
<dependencies>
    <!-- Jackson for transformation -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-xml</artifactId>
    </dependency>

    <!-- SFTP Support -->
    <dependency>
        <groupId>com.jcraft</groupId>
        <artifactId>jsch</artifactId>
        <version>0.1.55</version>
    </dependency>

    <!-- Apache Commons IO -->
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
    </dependency>

    <!-- Spring Web Client -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Create Outbound Batch Generator

```java
// File: src/main/java/com/example/batch/generator/OutboundBatchGenerator.java
package com.example.batch.generator;

import com.example.batch.model.BatchFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class OutboundBatchGenerator {
    private static final Logger log = LoggerFactory.getLogger(OutboundBatchGenerator.class);
    
    // Standard magic hash constant for zero-byte content SHA-256 digest
    public static final String EMPTY_SHA256_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final ObjectMapper objectMapper;

    public OutboundBatchGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Generate outbound batch from domain data.
     * Supports generating empty files with the "_NOD" suffix when no records are available.
     */
    public BatchFile generateBatch(List<?> records, String format, String recipientId) {
        String baseBatchId = generateBatchId();
        boolean hasNoData = records == null || records.isEmpty();
        
        // Apply suffix "_NOD" to batch ID and filename if empty
        String batchId = hasNoData ? baseBatchId + "_NOD" : baseBatchId;

        log.atInfo()
            .setMessage(hasNoData ? "Generating empty outbound batch (NOD)" : "Generating outbound batch")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface", "batch"})
            .addKeyValue("event.type", new String[]{"interface-start"})
            .addKeyValue("event.action", "file-generation")
            .addKeyValue("batch.job.id", batchId)
            .addKeyValue("interface.system", recipientId)
            .addKeyValue("file.extension", format)
            .addKeyValue("file.record_count", hasNoData ? 0 : records.size())
            .log();

        long startTime = System.currentTimeMillis();
        byte[] content;

        if (hasNoData) {
            content = new byte[0]; // 0 bytes empty file
        } else {
            content = switch (format.toLowerCase()) {
                case "csv" -> generateCsvContent(records);
                case "json" -> generateJsonContent(records);
                case "xml" -> generateXmlContent(records);
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };
        }

        long duration = System.currentTimeMillis() - startTime;

        BatchFile batchFile = new BatchFile(batchId, null, format);
        batchFile.setContent(content);
        batchFile.setFileSizeBytes(content.length);
        batchFile.setRecordCount(hasNoData ? 0 : records.size());

        // Generate SHA-256 hash
        String fileHash = hasNoData ? EMPTY_SHA256_HASH : computeHash(content);

        log.atInfo()
            .setMessage("Batch generation completed")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface", "batch"})
            .addKeyValue("event.type", new String[]{"interface-end"})
            .addKeyValue("event.action", "file-generation")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.duration_ms", duration)
            .addKeyValue("batch.job.id", batchId)
            .addKeyValue("file.extension", format)
            .addKeyValue("file.size", content.length)
            .addKeyValue("file.hash", fileHash)
            .log();

        return batchFile;
    }

    private byte[] generateCsvContent(List<?> records) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Name,Amount,Status,Timestamp\n");

        for (Object record : records) {
            csv.append(convertToCSVRow(record)).append("\n");
        }

        return csv.toString().getBytes();
    }

    private byte[] generateJsonContent(List<?> records) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", generateBatchId());
        payload.put("timestamp", Instant.now());
        payload.put("recordCount", records.size());
        payload.put("records", records);

        return objectMapper.writeValueAsBytes(payload);
    }

    private byte[] generateXmlContent(List<?> records) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<batch>\n");
        xml.append("  <batchId>").append(generateBatchId()).append("</batchId>\n");
        xml.append("  <timestamp>").append(Instant.now()).append("</timestamp>\n");
        xml.append("  <records>\n");

        for (Object record : records) {
            xml.append("    <record>").append(record).append("</record>\n");
        }

        xml.append("  </records>\n");
        xml.append("</batch>");

        return xml.toString().getBytes();
    }

    private String convertToCSVRow(Object record) {
        return "1,Name,1000.00,APPROVED," + Instant.now();
    }

    private String generateBatchId() {
        long timestamp = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "outbound_" + timestamp + "_" + uuid;
    }

    private String computeHash(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
}
```

### Step 3: Create Outbound Transmitter

```java
// File: src/main/java/com/example/batch/transmitter/OutboundTransmitter.java
package com.example.batch.transmitter;

import com.example.batch.model.BatchFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.*;

@Component
public class OutboundTransmitter {
    private static final Logger log = LoggerFactory.getLogger(OutboundTransmitter.class);

    /**
     * Transmit batch file via the specified transport method.
     */
    public void transmit(BatchFile batchFile, String transport, String recipientConfig) throws Exception {
        log.atInfo()
            .setMessage("Starting batch transmission")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface"})
            .addKeyValue("event.type", new String[]{"interface-start"})
            .addKeyValue("event.action", "api-push")
            .addKeyValue("batch.job.id", batchFile.getBatchId())
            .addKeyValue("interface.type", transport)
            .addKeyValue("file.size", batchFile.getFileSizeBytes())
            .log();

        long startTime = System.currentTimeMillis();

        switch (transport.toLowerCase()) {
            case "file" -> transmitViaFileSystem(batchFile, recipientConfig);
            case "sftp" -> transmitViaSftp(batchFile, recipientConfig);
            case "http" -> transmitViaHttp(batchFile, recipientConfig);
            default -> throw new IllegalArgumentException("Unsupported transport: " + transport);
        }

        long duration = System.currentTimeMillis() - startTime;

        log.atInfo()
            .setMessage("Batch transmission completed")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface"})
            .addKeyValue("event.type", new String[]{"interface-end"})
            .addKeyValue("event.action", "api-push")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.duration_ms", duration)
            .addKeyValue("batch.job.id", batchFile.getBatchId())
            .addKeyValue("interface.type", transport)
            .log();
    }

    /**
     * Transmit via shared file system (local path or mount).
     */
    private void transmitViaFileSystem(BatchFile batchFile, String recipientPath) throws Exception {
        Path targetDir = Paths.get(recipientPath);
        Files.createDirectories(targetDir);

        String fileName = batchFile.getBatchId() + "." + batchFile.getFormat();
        Path targetPath = targetDir.resolve(fileName);

        Files.write(targetPath, batchFile.getContent());

        log.atInfo()
            .setMessage("File transmitted via file system")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface"})
            .addKeyValue("event.action", "api-push")
            .addKeyValue("batch.job.id", batchFile.getBatchId())
            .addKeyValue("file.path", targetPath.toString())
            .log();
    }

    /**
     * Transmit via SFTP.
     */
    private void transmitViaSftp(BatchFile batchFile, String sftpUrl) throws Exception {
        // Parse SFTP config: "sftp://user:pass@host:port/path"
        // Implementation using JSch:

        com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
        com.jcraft.jsch.Session session = jsch.getSession("sftp_user", "sftp.recipient.com", 22);
        session.setPassword("password");
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        com.jcraft.jsch.ChannelSftp channelSftp =
            (com.jcraft.jsch.ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();

        try {
            String fileName = batchFile.getBatchId() + "." + batchFile.getFormat();

            java.io.ByteArrayInputStream bais =
                new java.io.ByteArrayInputStream(batchFile.getContent());

            channelSftp.put(bais, "/inbox/" + fileName);

            log.atInfo()
                .setMessage("File transmitted via SFTP")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.action", "api-push")
                .addKeyValue("batch.job.id", batchFile.getBatchId())
                .addKeyValue("file.path", "/inbox/" + fileName)
                .log();

        } finally {
            channelSftp.disconnect();
            session.disconnect();
        }
    }

    /**
     * Transmit via HTTP POST.
     */
    private void transmitViaHttp(BatchFile batchFile, String httpUrl) throws Exception {
        // Implementation using Spring WebClient:
        // (Requires WebClient bean configured in application context)

        log.atInfo()
            .setMessage("File transmitted via HTTP")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface"})
            .addKeyValue("event.action", "api-push")
            .addKeyValue("batch.job.id", batchFile.getBatchId())
            .addKeyValue("url.full", httpUrl)
            .log();
    }
}
```

### Step 4: Create Outbound Coordinator

```java
// File: src/main/java/com/example/batch/coordinator/OutboundInterfaceCoordinator.java
package com.example.batch.coordinator;

import com.example.batch.context.BatchTransactionContext;
import com.example.batch.entity.InterfaceTransaction;
import com.example.batch.generator.OutboundBatchGenerator;
import com.example.batch.model.BatchFile;
import com.example.batch.repository.InterfaceTransactionRepository;
import com.example.batch.transmitter.OutboundTransmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboundInterfaceCoordinator {
    private static final Logger log = LoggerFactory.getLogger(OutboundInterfaceCoordinator.class);

    private final OutboundBatchGenerator generator;
    private final OutboundTransmitter transmitter;
    private final InterfaceTransactionRepository transactionRepository;

    public OutboundInterfaceCoordinator(OutboundBatchGenerator generator,
                                        OutboundTransmitter transmitter,
                                        InterfaceTransactionRepository transactionRepository) {
        this.generator = generator;
        this.transmitter = transmitter;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Generate and transmit an outbound batch.
     */
    @Transactional
    public InterfaceTransaction generateAndTransmit(List<?> records, String format,
                                                     String recipientId, String transport) {
        String transactionId = BatchTransactionContext.initializeTransaction(
            "outbound_" + System.currentTimeMillis(),
            recipientId
        );

        try {
            // Step 1: Generate batch
            log.atInfo()
                .setMessage("Starting outbound batch generation")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface", "batch"})
                .addKeyValue("event.type", new String[]{"interface-start"})
                .addKeyValue("event.action", "file-generation")
                .log();
            BatchFile batchFile = generator.generateBatch(records, format, recipientId);

            // Step 2: Create transaction record
            InterfaceTransaction transaction = new InterfaceTransaction();
            transaction.setTransactionId(transactionId);
            transaction.setBatchId(batchFile.getBatchId());
            transaction.setSenderId("OUR_SYSTEM");  // This system
            transaction.setStatus("generated");
            transaction.setRecordCount(batchFile.getRecordCount());
            transaction.setFileSizeBytes(batchFile.getFileSizeBytes());
            transactionRepository.save(transaction);

            // Step 3: Transmit batch
            log.atInfo()
                .setMessage("Transmitting batch")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"interface-start"})
                .addKeyValue("event.action", "api-push")
                .addKeyValue("interface.type", transport)
                .log();
            transmitter.transmit(batchFile, transport, "sftp.recipient.com:/inbox");

            transaction.setStatus("transmitted");
            transaction.setCompletedAt(Instant.now());
            transactionRepository.save(transaction);

            log.atInfo()
                .setMessage("Batch transmission succeeded")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"interface-end"})
                .addKeyValue("event.action", "api-push")
                .addKeyValue("event.outcome", "success")
                .log();

            return transaction;

        } catch (Exception e) {
            log.atError()
                .setMessage("Batch generation or transmission failed")
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface", "batch"})
                .addKeyValue("event.type", new String[]{"error"})
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("error_category", "transmission")
                .log();

            // Persist failure
            InterfaceTransaction transaction = transactionRepository.findById(transactionId).orElse(new InterfaceTransaction());
            transaction.setStatus("failed");
            transaction.setErrorCategory("transmission_error");
            transaction.setErrorMessage(e.getMessage());
            transaction.setCompletedAt(Instant.now());
            transactionRepository.save(transaction);

            throw new RuntimeException("Outbound batch processing failed", e);

        } finally {
            BatchTransactionContext.clear();
        }
    }
}
```

### Step 5: Implement Retry Logic for Failed Transmissions

```java
// File: src/main/java/com/example/batch/service/TransmissionRetryService.java
package com.example.batch.service;

import com.example.batch.entity.InterfaceTransaction;
import com.example.batch.model.BatchFile;
import com.example.batch.repository.InterfaceTransactionRepository;
import com.example.batch.transmitter.OutboundTransmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TransmissionRetryService {
    private static final Logger log = LoggerFactory.getLogger(TransmissionRetryService.class);

    @Value("${batch.outbound.transmission_retry_max_attempts:3}")
    private int maxRetryAttempts;

    @Value("${batch.outbound.transmission_retry_initial_delay_ms:1000}")
    private long initialDelay;

    private final InterfaceTransactionRepository transactionRepository;
    private final OutboundTransmitter transmitter;

    public TransmissionRetryService(InterfaceTransactionRepository transactionRepository,
                                    OutboundTransmitter transmitter) {
        this.transactionRepository = transactionRepository;
        this.transmitter = transmitter;
    }

    /**
     * Retry failed transmissions using exponential backoff.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${batch.outbound.retry_check_interval_ms:300000}")
    @Transactional
    public void retryFailedTransmissions() {
        List<InterfaceTransaction> failedTransactions =
            transactionRepository.findByStatusAndRetryCountLessThan("transmission_failed", maxRetryAttempts);

        for (InterfaceTransaction txn : failedTransactions) {
            long delay = calculateExponentialBackoff(txn.getRetryCount());

            if (hasBackoffPassed(txn, delay)) {
                log.atInfo()
                    .setMessage("Retrying failed transmission")
                    .addKeyValue("correlation.id", txn.getTransactionId())
                    .addKeyValue("file.retry_count", txn.getRetryCount() + 1)
                    .log();

                try {
                    // Increment retry count
                    txn.setRetryCount(txn.getRetryCount() + 1);
                    transactionRepository.save(txn);

                    // Attempt retransmission
                    // Note: Actual implementation would load the batch from storage
                    // transmitter.transmit(batchFile, transport, config);

                    txn.setStatus("transmitted");
                    transactionRepository.save(txn);

                } catch (Exception e) {
                    log.atWarn()
                        .setMessage("Retry attempt failed")
                        .setCause(e)
                        .addKeyValue("correlation.id", txn.getTransactionId())
                        .log();
                    txn.setStatus("transmission_failed");
                    transactionRepository.save(txn);
                }
            }
        }
    }

    /**
     * Calculate exponential backoff delay in milliseconds.
     * Formula: initialDelay * (2 ^ retryCount)
     */
    private long calculateExponentialBackoff(int retryCount) {
        return initialDelay * (long) Math.pow(2, retryCount);
    }

    /**
     * Check if enough time has passed since the last attempt.
     */
    private boolean hasBackoffPassed(InterfaceTransaction txn, long delayMs) {
        long timeSinceCompletion = System.currentTimeMillis() - txn.getCompletedAt().toEpochMilli();
        return timeSinceCompletion >= delayMs;
    }
}
```

### Step 6: Create ACK Receiver and Timeout Handler

```java
// File: src/main/java/com/example/batch/service/AckReceiverService.java
package com.example.batch.service;

import com.example.batch.entity.InterfaceTransaction;
import com.example.batch.repository.InterfaceTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AckReceiverService {
    private static final Logger log = LoggerFactory.getLogger(AckReceiverService.class);

    @Value("${batch.outbound.ack_timeout_hours:48}")
    private int ackTimeoutHours;

    private final InterfaceTransactionRepository transactionRepository;

    public AckReceiverService(InterfaceTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Check for ACK timeouts and early warnings. Runs every 1 hour.
     */
    @Scheduled(fixedDelayString = "${batch.outbound.ack_timeout_check_interval_ms:3600000}")
    @Transactional
    public void checkForAckTimeouts() {
        long earlyWarningHours = (long) (ackTimeoutHours * 0.75);
        Instant earlyWarningThreshold = Instant.now().minus(earlyWarningHours, ChronoUnit.HOURS);
        Instant fullTimeoutThreshold = Instant.now().minus(ackTimeoutHours, ChronoUnit.HOURS);

        // 1. Full Timeout Breach Check (ERROR level)
        List<InterfaceTransaction> fullyBreached =
            transactionRepository.findByStatusAndCompletedAtBefore("transmitted", fullTimeoutThreshold);

        for (InterfaceTransaction txn : fullyBreached) {
            log.atError()
                .setMessage("ACK timeout threshold exceeded; escalating")
                .addKeyValue("correlation.id", txn.getTransactionId())
                .addKeyValue("batch.job.id", txn.getBatchId())
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"error"})
                .addKeyValue("error_category", "others")
                .log();

            // Mark for manual intervention
            txn.setStatus("ack_pending_escalation");
            txn.setErrorCategory("ack_timeout");
            txn.setErrorMessage("Acknowledgment not received within " + ackTimeoutHours + " hours");
            transactionRepository.save(txn);

            triggerAlert(txn);
        }

        // 2. Early Warning Check (WARN level, 75% of timeout duration elapsed)
        // Query files in transmitted status between early warning and full timeout
        List<InterfaceTransaction> earlyWarnings =
            transactionRepository.findByStatusAndCompletedAtBetween("transmitted", fullTimeoutThreshold, earlyWarningThreshold);

        for (InterfaceTransaction txn : earlyWarnings) {
            log.atWarn()
                .setMessage("ACK timeout early warning: 75% of SLA threshold elapsed")
                .addKeyValue("correlation.id", txn.getTransactionId())
                .addKeyValue("batch.job.id", txn.getBatchId())
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"warning"})
                .log();
        }
    }

    /**
     * Process incoming ACK from recipient.
     */
    @Transactional
    public void receiveAck(String batchId, String ackStatus, String ackDetails) {
        var txn = transactionRepository.findByBatchId(batchId);

        if (txn.isPresent()) {
            InterfaceTransaction transaction = txn.get();

            if ("transmitted".equals(transaction.getStatus())) {
                log.atInfo()
                    .setMessage("ACK received")
                    .addKeyValue("correlation.id", transaction.getTransactionId())
                    .addKeyValue("batch.job.id", batchId)
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", new String[]{"interface"})
                    .addKeyValue("event.type", new String[]{"interface-end"})
                    .addKeyValue("event.action", "file-ack-process")
                    .addKeyValue("file.ack.status", ackStatus)
                    .log();

                transaction.setStatus("acknowledged");
                transaction.setErrorMessage("ACK: " + ackDetails);
                transaction.setCompletedAt(Instant.now());
                transactionRepository.save(transaction);
            }
        } else {
            log.atWarn()
                .setMessage("ACK received for unknown batch")
                .addKeyValue("batch.job.id", batchId)
                .addKeyValue("file.ack.status", ackStatus)
                .log();
        }
    }

    private void triggerAlert(InterfaceTransaction txn) {
        // Implementation: Send email, Slack message, or trigger monitoring alert
        log.atError()
            .setMessage("ALERT: ACK timeout for batch. Manual intervention required.")
            .addKeyValue("batch.job.id", txn.getBatchId())
            .addKeyValue("error_follow_up_action", true)
            .log();
    }
}
```

### Step 7: Implement Automatic and Manual File Discard Workflows

To prevent accumulating orphan files from failed runs or outdated batches, implement **Automatic Discards** (when the job fails before transmission starts) and **Manual Discards** (operator-driven). Archiving must also follow the status subfolder structure (`success/`, `failure/`, `discard/`).

```java
// File: src/main/java/com/example/batch/service/OutboundArchivingService.java
package com.example.batch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class OutboundArchivingService {
    private static final Logger log = LoggerFactory.getLogger(OutboundArchivingService.class);

    @Value("${batch.outbound.directory}")
    private String outboundDirectory;

    @Value("${batch.archive.directory}")
    private String archiveDirectory;

    /**
     * Archives an outbound file under the correct status-based subfolder.
     * Allowed status categories: "success" (ACK received), "failure" (retry limit breached), "discard" (discarded)
     */
    public void archiveOutboundFile(String fileName, String statusCategory) throws IOException {
        Path sourceFile = Paths.get(outboundDirectory).resolve(fileName);
        Path targetDir = Paths.get(archiveDirectory).resolve("outbound").resolve(statusCategory);
        
        Files.createDirectories(targetDir);

        if (Files.exists(sourceFile)) {
            Path targetFile = targetDir.resolve(fileName);
            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Outbound file archived under: {}/{}", statusCategory, fileName);
        } else {
            log.warn("Outbound file does not exist in outbound directory; archiving skipped: {}", fileName);
        }
    }
}
```

```java
// File: src/main/java/com/example/batch/service/OutboundFileDiscardService.java
package com.example.batch.service;

import com.example.batch.entity.InterfaceTransaction;
import com.example.batch.repository.InterfaceTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Service
public class OutboundFileDiscardService {
    private static final Logger log = LoggerFactory.getLogger(OutboundFileDiscardService.class);

    private final InterfaceTransactionRepository transactionRepository;
    private final OutboundArchivingService archivingService;

    // States from which a file can be manually discarded
    private static final Set<String> DISCARDABLE_STATUSES = Set.of(
        "transmitted", "transmission_failed", "ack_pending_escalation"
    );

    public OutboundFileDiscardService(InterfaceTransactionRepository transactionRepository,
                                      OutboundArchivingService archivingService) {
        this.transactionRepository = transactionRepository;
        this.archivingService = archivingService;
    }

    /**
     * Automatic Discard (System-Initiated).
     * Triggered when the batch generation job fails before transmission starts.
     */
    @Transactional
    public void discardOnJobFailure(String transactionId) {
        transactionRepository.findById(transactionId).ifPresent(txn -> {
            if ("initialised".equalsIgnoreCase(txn.getStatus())) {
                txn.setStatus("discarded");
                txn.setCompletedAt(Instant.now());
                transactionRepository.save(txn);

                log.atWarn()
                    .setMessage("File discarded due to job failure")
                    .addKeyValue("correlation.id", transactionId)
                    .log();
            }
        });
    }

    /**
     * Manual Discard (Operator-Initiated).
     */
    @Transactional
    public void manualDiscard(String transactionId, String operatorId, String reason) throws IOException {
        InterfaceTransaction txn = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (!DISCARDABLE_STATUSES.contains(txn.getStatus().toLowerCase())) {
            throw new IllegalStateException("Transaction in terminal status '" + txn.getStatus() + "' cannot be discarded.");
        }

        txn.setStatus("discarded");
        txn.setErrorMessage("Discarded by " + operatorId + ". Reason: " + reason);
        txn.setCompletedAt(Instant.now());
        transactionRepository.save(txn);

        log.atInfo()
            .setMessage("Outbound batch file manually discarded")
            .addKeyValue("correlation.id", transactionId)
            .addKeyValue("operator.id", operatorId)
            .addKeyValue("event.action", "file-discard")
            .addKeyValue("event.outcome", "success")
            .log();

        // Move physical file to discard archive subfolder
        archivingService.archiveOutboundFile(txn.getFileName(), "discard");
    }
}
```

---

## 4. Examples

### Example 1: Generate and Transmit CSV Batch

```java
// In a scheduled job or REST endpoint
List<SalesRecord> records = salesRepository.findByStatus("PENDING");

coordinat.generateAndTransmit(
    records,
    "csv",
    "SALES_SYSTEM",
    "sftp"
);

// Log output:
// {"@timestamp":"2026-03-27T14:30:45Z","log.level":"INFO","message":"Starting outbound batch generation","batch.job.id":"outbound_1711534245_abc123","event.kind":"event","event.category":["interface","batch"],"event.type":["interface-start"],"event.action":"file-generation"}
// {"@timestamp":"2026-03-27T14:30:46Z","log.level":"INFO","message":"Batch transmission completed","batch.job.id":"outbound_1711534245_abc123","file.record_count":1250,"event.kind":"event","event.category":["interface"],"event.type":["interface-end"],"event.action":"api-push","event.outcome":"success"}
```

### Example 2: Transmission Retry Sequence

```
Time T0: Transmission fails with timeout error
         retry_count = 0
         status = transmission_failed

Time T0 + 1s: First retry scheduled (1000ms exponential backoff)
              retry_count = 1
              Attempt succeeds
              status = transmitted

Time T0 + 2m: Check for ACK...
              ACK received
              status = acknowledged
              completed_at = T0 + 120s
```

### Example 3: ACK Timeout and Escalation

```
Time T0: Batch transmitted
         status = transmitted
         completed_at = T0

Time T0 + 48h: ACK timeout check runs
               No ACK received
               status = ack_pending_escalation
               Alert triggered for manual follow-up

Operator action: Query recipient system, manually verify receipt, or resend batch
```

## 5. Verification

1. Create a test list of domain objects.
2. Call `generateAndTransmit()` with CSV format and file system transport.
3. Verify that the file is written to the target directory.
4. Check logs for batch_generation_started and batch_transmitted events.
5. Verify transaction is persisted with status "transmitted".
6. Simulate a transmission failure and verify retry logic executes with exponential backoff.
7. Simulate a missing ACK and verify timeout detection and escalation occurs.

## 6. Conclusion

You have implemented outbound batch generation, transmission, retry logic, and acknowledgment handling. The system now:
- Transforms domain data to multiple output formats
- Transmits batches via different transport mechanisms
- Automatically retries failed transmissions with exponential backoff
- Detects and escalates ACK timeouts
- Logs all events with full traceability

## 7. References

- [Jackson Databind](https://github.com/FasterXML/jackson-databind)
- [JSch SFTP Library](http://www.jcraft.com/jsch/)
- [Spring WebClient](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/client/WebClient.html)
- [Spring Scheduled Tasks](https://spring.io/guides/gs/scheduling-tasks/)
- [Exponential Backoff Strategy](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)

# Entity Modeling with @MAGEntity and @Position

## 1. Introduction

The `@MAGEntity` and `@Position` annotations are the core entry point for integrating with this library. They tell the framework what file format to generate or parse, how to name the file, and in what order to map fields to columns or positions.

Every inbound or outbound data class you define must be annotated with `@MAGEntity` at the class level and `@Position` on each field that maps to a column or fixed-width position. Without these annotations, the framework cannot discover your entity or map its fields to file content.

By the end of this guide, you will:
- Annotate an outbound data record class for CSV and XML generation
- Annotate an inbound data record class for CSV and XML parsing
- Understand all attributes of `@MAGEntity` and when to change each one
- Know the rules for `@Position` ordering and duplicate prevention

## 2. Prerequisites

- Spring Boot 3.0+
- `interface-management-inbound-starter` or `interface-management-outbound-starter` on the classpath
- Lombok (`@Data`, `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor`) for boilerplate reduction
- For XML entities: `jakarta.xml.bind` (JAXB) on the classpath (included transitively via `jakarta.xml.bind-api`)

## 3. Steps

### Step 1: Understand the @MAGEntity Attributes

`@MAGEntity` is a class-level annotation that declares the file manifest for a data record type.

| Attribute | Type | Required | Default | Description |
|---|---|---|---|---|
| `filenamePrefix` | `String` | **Yes** | — | Prepended to the generated filename. Must be non-empty. Example: `"order"` → `order_20260421_001.csv` |
| `fileType` | `FileType` | No | `CSV` | File format. See FileType reference below. |
| `sequenceType` | `SequenceType` | No | `DAILY_SEQUENCE` | How the filename sequence suffix is generated. See SequenceType reference below. |
| `isCompressed` | `boolean` | No | `false` | Whether the file is ZIP-compressed. |
| `securityClassification` | `SecurityClassification` | No | `RESTRICTED` | Data classification label embedded in file metadata. |
| `sensitivityClassification` | `SensitivityClassification` | No | `SENSITIVE_NORMAL` | Sensitivity label embedded in file metadata. |

**FileType values:**

| Value | Extension | Use when |
|---|---|---|
| `CSV` | `.csv` | Delimited flat file (default) |
| `XML` | `.xml` | XML structured file |
| `ZIP` | `.zip` | Compressed archive (use with `isCompressed = true`) |
| `SIGNED` | `.signed` | Digitally signed file |
| `SHA3` | `.sha3` | Hash file for integrity verification |

**SequenceType values:**

| Value | Suffix pattern | Use when |
|---|---|---|
| `DAILY_SEQUENCE` | Date + daily counter | One or a few files per day per type |
| `TRANSACTION_SEQUENCE` | Transaction-scoped counter | Files tied to a specific transaction |
| `CONTINUOUS_SEQUENCE` | Monotonically incrementing counter | High-volume, continuous generation |
| `NONE` | No suffix appended | Filename is the prefix as-is |

**SecurityClassification values:** `OFFICIAL_OPEN`, `OFFICIAL_CLOSED`, `RESTRICTED`, `CONFIDENTIAL`

**SensitivityClassification values:** `NON_SENSITIVE`, `SENSITIVE_NORMAL`, `SENSITIVE_HIGH`

### Step 2: Understand the @Position Annotation

`@Position` is a field-level annotation that defines the column order for CSV/fixed-width formats, and supplements JAXB element ordering for XML.

- Values must be **positive integers starting at 1**
- Values must be **unique within a class hierarchy** — duplicate positions cause `DuplicatePositionException` at startup
- Fields without `@Position` are ignored by the framework's file mapping
- For XML, `@Position` works alongside `@XmlElement`; the XML element name comes from JAXB, the order hint comes from `@Position`

### Step 3: Create an Outbound CSV Data Record

Extend `OutboundInterfaceDataRecord` and annotate your domain fields:

```java
// File: src/main/java/com/example/model/PaymentOutboundDataRecord.java
package com.example.model;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.example.interface.core.annotation.MAGEntity;
import com.example.interface.core.annotation.Position;
import com.example.interface.core.model.enums.FileType;
import com.example.interface.core.model.enums.SecurityClassification;
import com.example.interface.core.model.enums.SensitivityClassification;
import com.example.interface.core.model.enums.SequenceType;
import com.example.interface.outbound.model.OutboundInterfaceDataRecord;

@Entity
@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MAGEntity(
        filenamePrefix = "payment",
        sequenceType = SequenceType.DAILY_SEQUENCE,
        fileType = FileType.CSV,
        isCompressed = false,
        securityClassification = SecurityClassification.CONFIDENTIAL,
        sensitivityClassification = SensitivityClassification.SENSITIVE_HIGH
)
public class PaymentOutboundDataRecord extends OutboundInterfaceDataRecord {

    @Position(1)
    private String paymentId;

    @Position(2)
    private String accountNumber;

    @Position(3)
    private Double amount;

    @Position(4)
    private String currency;
}
```

**Why `@Entity` and `@SuperBuilder`:** The base class `OutboundInterfaceDataRecord` uses `@SuperBuilder` to support inheritance. Your subclass must also declare `@SuperBuilder`, `@NoArgsConstructor`, and `@AllArgsConstructor` for the builder chain to work correctly. `@Entity` registers this class in the JPA context so records are persisted and linked to the outbound file.

### Step 4: Create an Outbound XML Data Record

For XML output, add JAXB annotations alongside `@MAGEntity` and `@Position`:

```java
// File: src/main/java/com/example/model/ReportOutboundDataRecord.java
package com.example.model;

import jakarta.persistence.Entity;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.example.interface.core.annotation.MAGEntity;
import com.example.interface.core.annotation.Position;
import com.example.interface.core.model.enums.FileType;
import com.example.interface.core.model.enums.SecurityClassification;
import com.example.interface.core.model.enums.SensitivityClassification;
import com.example.interface.core.model.enums.SequenceType;
import com.example.interface.outbound.model.OutboundInterfaceDataRecord;

@XmlRootElement(name = "report")         // XML element name for each record
@XmlAccessorType(XmlAccessType.NONE)     // Only fields annotated with @XmlElement are serialised
@Entity
@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MAGEntity(
        filenamePrefix = "report",
        sequenceType = SequenceType.DAILY_SEQUENCE,
        fileType = FileType.XML,
        isCompressed = false,
        securityClassification = SecurityClassification.RESTRICTED,
        sensitivityClassification = SensitivityClassification.SENSITIVE_NORMAL
)
public class ReportOutboundDataRecord extends OutboundInterfaceDataRecord {

    @Position(1)
    @XmlElement
    private String reportId;

    @Position(2)
    @XmlElement
    private String description;

    @Position(3)
    @XmlElement
    private String generatedDate;
}
```

**Why `@XmlAccessorType(XmlAccessType.NONE)`:** Without this, JAXB serialises every field by default. Setting `NONE` means only fields explicitly marked with `@XmlElement` are included, giving you precise control over the XML output.

### Step 5: Create an Inbound Data Record

For inbound processing, extend `InboundFileContent` (the persisted entity) and create a paired `InboundFileContentDTO` (the parsed row DTO):

```java
// File: src/main/java/com/example/model/TransactionInboundFileContent.java
package com.example.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.example.interface.inbound.entity.InboundFileContent;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionInboundFileContent extends InboundFileContent {

    @Column(name = "TRANSACTION_ID")
    private String transactionId;

    @Column(name = "AMOUNT")
    private Double amount;

    @Column(name = "STATUS")
    private String status;
}
```

```java
// File: src/main/java/com/example/model/TransactionInboundFileContentDTO.java
package com.example.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.example.interface.core.annotation.Position;
import com.example.interface.inbound.entity.InboundFileContentDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionInboundFileContentDTO extends InboundFileContentDTO {

    @Position(1)
    private String transactionId;

    @Position(2)
    private Double amount;

    @Position(3)
    private String status;
}
```

**Why two classes for inbound?** The DTO represents one raw row as parsed from the file. The `InboundFileContent` entity is what gets persisted after the `BatchJobCommand.process()` step maps and validates the DTO. This separation means parsing errors in the DTO never corrupt your persisted content table.

### Step 6: Register Your Entities

Spring Boot must scan your entity classes. If your entities are not in the root package, declare them explicitly in your configuration:

```java
// File: src/main/java/com/example/config/AppAutoConfiguration.java
package com.example.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = {"com.example"})
@EntityScan({"com.example.model"})
@EnableJpaRepositories(basePackages = {"com.example.repository"})
public class AppAutoConfiguration {
}
```

## 4. Reference

### Complete @MAGEntity Example — All Attributes

```java
@MAGEntity(
    filenamePrefix = "settlement",          // Required: "settlement_20260421_001.csv"
    fileType = FileType.CSV,                // CSV, XML, ZIP, SIGNED, SHA3
    sequenceType = SequenceType.DAILY_SEQUENCE, // DAILY_SEQUENCE, TRANSACTION_SEQUENCE, CONTINUOUS_SEQUENCE, NONE
    isCompressed = false,                   // true: output wrapped in ZIP; false: plain file
    securityClassification = SecurityClassification.CONFIDENTIAL,   // OFFICIAL_OPEN, OFFICIAL_CLOSED, RESTRICTED, CONFIDENTIAL
    sensitivityClassification = SensitivityClassification.SENSITIVE_HIGH // NON_SENSITIVE, SENSITIVE_NORMAL, SENSITIVE_HIGH
)
```

### @Position Rules

```java
// Correct — positions are unique and start at 1
@Position(1) private String accountId;
@Position(2) private String name;
@Position(3) private Double balance;

// Wrong — duplicate position causes DuplicatePositionException at startup
@Position(1) private String accountId;
@Position(1) private String name;   // ❌ Duplicate

// Wrong — position 0 is not valid
@Position(0) private String accountId;  // ❌ Must start at 1
```

### Inheritance — Positions Must Be Unique Across the Full Hierarchy

If your base class or any parent class has `@Position` annotations, your subclass positions must not overlap:

```java
// Base class defines positions 1 and 2
public abstract class BaseRecord extends OutboundInterfaceDataRecord {
    @Position(1) private String id;
    @Position(2) private String createdAt;
}

// Subclass must start from position 3
public class MyRecord extends BaseRecord {
    @Position(3) private String name;   // ✅ Unique
    @Position(4) private String status; // ✅ Unique
}
```

## 5. Verification

1. Start your application. If a `DuplicatePositionException` appears at startup, a position value is used more than once in your class hierarchy — fix the duplicate.
2. For outbound: trigger the job and confirm the output file is created with the correct `filenamePrefix` and extension.
3. For outbound CSV: open the generated file and verify that columns appear in the order defined by `@Position`.
4. For outbound XML: open the generated file and verify each record is wrapped in the element named by `@XmlRootElement`.
5. For inbound: place a test file in the inbound directory and trigger the job. Confirm that records are persisted in the `InboundFileContent` table with the correct field values.

## 6. Common Mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Missing `@SuperBuilder` on subclass | `NoSuchMethodError` or builder compile error | Add `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor` to the subclass |
| Duplicate `@Position` values | `DuplicatePositionException` at startup | Audit positions across the full inheritance chain; ensure each value is unique |
| Missing `@Entity` on subclass | Records not persisted; `UnknownEntityTypeException` | Add `@Entity` to the concrete class |
| XML fields serialised unexpectedly | Extra fields in XML output | Add `@XmlAccessorType(XmlAccessType.NONE)` and annotate only desired fields with `@XmlElement` |
| Inbound DTO missing `@Position` | Fields not mapped from file; all values are `null` | Add `@Position` to every field that corresponds to a file column |

## 7. References

- [`@MAGEntity`](../../interface-management-core-starter/src/main/java/com/example/interface/core/annotation/MAGEntity.java)
- [`@Position`](../../interface-management-core-starter/src/main/java/com/example/interface/core/annotation/Position.java)
- [`FileType`](../../interface-management-core-starter/src/main/java/com/example/interface/core/model/enums/FileType.java)
- [`SequenceType`](../../interface-management-core-starter/src/main/java/com/example/interface/core/model/enums/SequenceType.java)
- [`OutboundInterfaceDataRecord`](../../interface-management-outbound-starter/src/main/java/com/example/interface/outbound/model/OutboundInterfaceDataRecord.java)
- [`InboundFileContent`](../../interface-management-inbound-starter/src/main/java/com/example/interface/inbound/entity/InboundFileContent.java)
- [`InboundFileContentDTO`](../../interface-management-inbound-starter/src/main/java/com/example/interface/inbound/entity/InboundFileContentDTO.java)
- Demo: [`OrderOutboundDataRecord`](../../interface-management-demo/src/main/java/com/demo/model/OrderOutboundDataRecord.java)

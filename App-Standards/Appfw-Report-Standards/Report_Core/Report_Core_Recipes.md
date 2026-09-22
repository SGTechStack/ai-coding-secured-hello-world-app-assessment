# Report Generation — Implementation Recipes

> These recipes show how to implement report generation using pre-built JRXML templates. Integrators must build their own design model, validation, and export pipeline. Recipes use JasperReports, Spring Boot, JDBC, and standard Java I/O. All code targets JDK 17+.
>

---

## Recipe 1: Compile a JRXML Template

**Goal**: Compile a `.jrxml` template file into a `JasperReport` object at application startup. Compilation is expensive — do this once and cache the result. The compiled `JasperReport` is thread-safe for concurrent fill calls.

> For programmatic report design (no JRXML file), see `Report_Programmatic_Design_Recipes.md`.

Compilation must happen in a `@PostConstruct` method. If compilation fails, log at `ERROR` and rethrow — the Spring context must not finish starting with a broken template. Never catch the exception silently or defer compilation to the first request.

```java
import net.sf.jasperreports.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class ReportTemplateCache {

    private static final Logger log = LoggerFactory.getLogger(ReportTemplateCache.class);

    // Fixed at deployment time — never accept this path from callers (see Section 3.4).
    private static final String TEMPLATE_PATH = "src/main/resources/reports/my_report.jrxml";

    private JasperReport jasperReport;

    @PostConstruct
    void compile() {
        if (!TEMPLATE_PATH.endsWith(".jrxml")) {
            throw new ReportValidationException("Template path must end in .jrxml: " + TEMPLATE_PATH);
        }
        File templateFile = new File(TEMPLATE_PATH);
        if (!templateFile.exists() || !templateFile.canRead()) {
            throw new ReportValidationException("Template file does not exist or is not readable: " + TEMPLATE_PATH);
        }
        try {
            jasperReport = JasperCompileManager.compileReport(TEMPLATE_PATH);
        } catch (JRException e) {
            log.error("event.action=REPORT_PRE_FILL event.outcome=failure — template compilation failed, application cannot start: {}",
                    TEMPLATE_PATH, e);
            throw new IllegalStateException("JRXML template compilation failed: " + TEMPLATE_PATH, e);
        }
    }

    public JasperReport get() { return jasperReport; }
}
```

Inject `ReportTemplateCache` wherever the compiled report is needed. Do not recompile per request.

---

## Recipe 2: Classification and Sensitivity Types

**Goal**: Define the classification and sensitivity enums required for all PDF and XLSX exports. These are passed as template parameters when filling a JRXML-based report.

```java
public enum ReportSecurityClassification {
    OFFICIAL_OPEN, OFFICIAL_CLOSED, RESTRICTED, CONFIDENTIAL, GEMS_SECRET
}

public enum ReportSensitivity {
    NON_SENSITIVE, SENSITIVE_NORMAL, SENSITIVE_HIGH
}
```


---

## Recipe 3: Validate a Compiled JasperReport

**Goal**: Implement the post-compilation validation gate that runs after a JRXML template is compiled and before fill and export. Validation failures should throw domain exceptions that map to `400 Bad Request`.

> Pre-compilation validation (report name, classification, sensitivity, datasource, columns) is only required for programmatic design. See `Report_Programmatic_Recipes.md`.

```java
public class ReportValidator {

    /** Post-compilation: validate the compiled JasperReport before fill and export. */
    public void validate(JasperReport report) {
        if (report == null) {
            throw new ReportValidationException("JasperReport must not be null.");
        }
        if (report.getName() == null || report.getName().isBlank()) {
            throw new ReportValidationException("JasperReport must have a name.");
        }
        if (report.getFields() == null || report.getFields().length == 0) {
            throw new ReportValidationException("JasperReport must declare at least one field.");
        }
    }

    public void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new ReportValidationException("Actor is required.");
        }
    }

    public void validateClassification(ReportSecurityClassification classification,
                                       ReportSensitivity sensitivity) {
        if (classification == null) {
            throw new ReportValidationException("Security classification is required.");
        }
        if (sensitivity == null) {
            throw new ReportValidationException("Sensitivity level is required.");
        }
    }

    public void validateFilePath(String baseDir, String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new InvalidFilePathException("Output file path must not be null or blank.");
        }
        String decoded = URLDecoder.decode(outputPath, StandardCharsets.UTF_8);
        if (decoded.contains("..")) {
            throw new InvalidFilePathException("Path traversal detected in output path: " + outputPath);
        }
        if (!decoded.matches("^[\\w\\-./\\\\ ]+$")) {
            throw new InvalidFilePathException("Output file path contains invalid characters: " + outputPath);
        }
        Path base = Paths.get(baseDir).normalize();
        Path resolved = base.resolve(decoded).normalize();
        if (!resolved.startsWith(base)) {
            throw new InvalidFilePathException("Output path escapes the configured base directory: " + outputPath);
        }
    }
}
```

**Order of operations**: Call `validateActor()` and `validateFilePath()` before any file I/O. Call `validate(JasperReport)` after compilation but before fill. No file I/O should occur on an invalid input.

---

## Recipe 4: File Path Validation — Accepted and Rejected Examples

The implementation of `validateFilePath(String baseDir, String outputPath)` is on `ReportValidator` (Recipe 3). The examples below show which paths it accepts and which it rejects.

**Valid and invalid examples** (base dir: `/var/reports/generated`):

```
VALID:   output/myReport.pdf
VALID:   subdir/2025-annual.csv

INVALID: ../sensitive/data.pdf          ← .. anywhere rejected
INVALID: /etc/passwd                    ← absolute path outside base dir
INVALID: %2e%2e/sensitive/data.pdf      ← traversal via URL encoding
INVALID: report;rm -rf /               ← semicolon not in allowlist
INVALID: <name>.pdf                    ← angle bracket not in allowlist
```
---

## Recipe 5: Datasource Factory — Wrapping Input Types into JRDataSource

**Goal**: Accept the three supported input types and convert each to a `JRDataSource` for use with `JasperFillManager`.

```java
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.*;

public class DatasourceFactory {

    public JRDataSource build(Object input) throws JRException {
        if (input instanceof String csvPath) {
            return fromCsv(csvPath);
        }
        if (input instanceof ResultSet rs) {
            return new JRResultSetDataSource(rs);
        }
        if (input instanceof Map<?, ?> map) {
            return fromColumnMap((Map<String, List<Object>>) map);
        }
        if (input instanceof JRDataSource ds) {
            return ds; // pass-through
        }
        throw new JRException("Unsupported datasource type: " + input.getClass().getName());
    }

    private JRDataSource fromCsv(String path) throws JRException {
        try {
            JRCsvDataSource ds = new JRCsvDataSource(new File(path));
            ds.setUseFirstRowAsHeader(true);
            return ds;
        } catch (FileNotFoundException e) {
            throw new JRException("CSV datasource file not found: " + path, e);
        }
    }

    private JRDataSource fromColumnMap(Map<String, List<Object>> columnMap) {
        // JasperReports expects a row-oriented collection: List<Map<String, Object>>
        // Transpose from column-oriented to row-oriented
        int rowCount = columnMap.values().stream().mapToInt(List::size).max().orElse(0);
        List<Map<String, Object>> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, List<Object>> entry : columnMap.entrySet()) {
                Object value = i < entry.getValue().size() ? entry.getValue().get(i) : null;
                row.put(entry.getKey(), value != null ? value.toString() : "");
            }
            rows.add(row);
        }
        return new JRMapCollectionDataSource(rows);
    }
}
```

**Notes**:
*   The caller owns the `ResultSet` lifecycle. Close it after the export call returns — do not close it inside `DatasourceFactory`.
*   Map values are converted to `String` via `Object.toString()`. If the JRXML template declares a non-`String` field type, type coercion will occur at fill time — ensure field types match.

---

## Recipe 6: Output Directory Configuration

**Goal**: Centralise the base output directory in application configuration. Callers supply a relative output path; `validateFilePath` (Recipe 4) anchors it to this base at validation time.

### Spring Boot configuration property

```java
@ConfigurationProperties(prefix = "report")
@Component
public class ReportProperties {

    private String outputDirectoryPath = "./";

    public String getOutputDirectoryPath() { return outputDirectoryPath; }
    public void setOutputDirectoryPath(String v) { this.outputDirectoryPath = v; }
}
```

### application.yaml

```yaml
report:
  outputDirectoryPath: /var/reports/generated
```

### Creating the output directory before writing

```java
Path outputFile = Path.of(outputPath);
Files.createDirectories(outputFile.getParent()); // Creates parent dirs if they don't exist
```

---

## Recipe 7: Fill and Export

**Goal**: Fill a compiled `JasperReport` with data and export it to the target format. These steps run after compilation (Recipe 1) and validation (Recipe 3) have completed.

### Fill

```java
// Parameters are injected into the template at fill time.
// Security classification and sensitivity must be included here if the template
// declares them as parameters (i.e. $P{CLASSIFICATION} / $P{SENSITIVITY} in the JRXML).
Map<String, Object> parameters = new HashMap<>();
parameters.put("REPORT_TITLE",   "Monthly Sales Report");
parameters.put("CLASSIFICATION", ReportSecurityClassification.OFFICIAL_CLOSED);
parameters.put("SENSITIVITY",    ReportSensitivity.SENSITIVE_NORMAL);

JRDataSource datasource = datasourceFactory.build(datasourceInput); // Recipe 5
JasperPrint print = JasperFillManager.fillReport(jasperReport, parameters, datasource);
```

`JasperFillManager.fillReport()` iterates the datasource row by row, stamping the detail band once per row into `JasperPrint`. The `JasperPrint` object is then passed to an exporter.

> If the classification tag is embedded directly in the JRXML as static text rather than a parameter, omit `CLASSIFICATION` and `SENSITIVITY` from the map — the template already has the values baked in. Using parameters is preferable when the classification may vary per report instance.

### Export — PDF

```java
try (OutputStream out = new FileOutputStream(outputPath)) {
    JRPdfExporter exporter = new JRPdfExporter();
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
    exporter.exportReport();
}
```

### Export — XLSX

```java
try (OutputStream out = new FileOutputStream(outputPath)) {
    JRXlsxExporter exporter = new JRXlsxExporter();
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
    exporter.exportReport();
}
```

### Export — CSV

```java
try (OutputStream out = new FileOutputStream(outputPath)) {
    JRCsvExporter exporter = new JRCsvExporter();
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleWriterExporterOutput(out));
    exporter.exportReport();
}
```

---

## Recipe 8: Wiring It All Together — Export Service

**Goal**: Combine all previous recipes into a single service that callers invoke per format.

```java
@Service
public class ReportExportService {

    private final JasperReport       jasperReport;     // compiled once at startup — Recipe 1
    private final ReportValidator    validator;         // Recipe 3
    private final DatasourceFactory  datasourceFactory; // Recipe 5
    private final ReportProperties   reportProperties;  // Recipe 6

    // Constructor injection omitted for brevity

    /** Post-compilation validation — runs once at startup against the cached JasperReport. */
    @PostConstruct
    void validateCachedReport() {
        validator.validate(jasperReport);
    }

    /**
     * @param actor  The identity initiating the export. For user requests, resolve from Spring Security
     *               before calling (e.g. {@code SecurityContextHolder.getContext().getAuthentication().getName()}).
     *               For batch/system reports, pass a descriptive identifier (e.g. {@code "BATCH_JOB:monthly-sales"}).
     */
    public void exportPdf(String actor, Object datasourceInput, String outputPath,
                          ReportSecurityClassification classification, ReportSensitivity sensitivity)
            throws JRException, IOException {
        // Pre-fill validation — Recipe 3 + Recipe 4
        validator.validateActor(actor);
        validator.validateClassification(classification, sensitivity);
        validator.validateFilePath(reportProperties.getOutputDirectoryPath(), outputPath);

        // Create output directory — Recipe 6
        Files.createDirectories(Path.of(outputPath).getParent());

        // Fill — Recipe 7; inject classification and sensitivity as template parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CLASSIFICATION", classification);
        parameters.put("SENSITIVITY",    sensitivity);
        JasperPrint print;
        try {
            print = fill(datasourceInput, parameters);
        } catch (JRException e) {
            log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
                "REPORT_FILL", "failure", jasperReport.getName(), "PDF", logSafeActor(actor), e);
            throw e;
        }

        // Export — Recipe 7; delete partial file and log failure if export fails mid-write
        File outFile = new File(outputPath);
        try (OutputStream out = new FileOutputStream(outFile)) {
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.exportReport();
        } catch (JRException | IOException e) {
            Files.deleteIfExists(outFile.toPath());
            log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
                "REPORT_EXPORT", "failure", jasperReport.getName(), "PDF", logSafeActor(actor), e);
            throw e;
        }

        log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_EXPORT", "success", jasperReport.getName(), "PDF", logSafeActor(actor));
    }

    /**
     * @param actor  See {@link #exportPdf} for actor conventions.
     */
    public void exportXlsx(String actor, Object datasourceInput, String outputPath,
                           ReportSecurityClassification classification, ReportSensitivity sensitivity)
            throws JRException, IOException {
        validator.validateActor(actor);
        validator.validateClassification(classification, sensitivity);
        validator.validateFilePath(reportProperties.getOutputDirectoryPath(), outputPath);

        Files.createDirectories(Path.of(outputPath).getParent());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CLASSIFICATION", classification);
        parameters.put("SENSITIVITY",    sensitivity);
        JasperPrint print;
        try {
            print = fill(datasourceInput, parameters);
        } catch (JRException e) {
            log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
                "REPORT_FILL", "failure", jasperReport.getName(), "XLSX", logSafeActor(actor), e);
            throw e;
        }

        File outFile = new File(outputPath);
        try (OutputStream out = new FileOutputStream(outFile)) {
            JRXlsxExporter exporter = new JRXlsxExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.exportReport();
        } catch (JRException | IOException e) {
            Files.deleteIfExists(outFile.toPath());
            log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
                "REPORT_EXPORT", "failure", jasperReport.getName(), "XLSX", logSafeActor(actor), e);
            throw e;
        }

        log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_EXPORT", "success", jasperReport.getName(), "XLSX", logSafeActor(actor));
    }

    /**
     * @param actor           See {@link #exportPdf} for actor conventions.
     * @param outputDirectory Directory to write the file into. The filename is constructed by the
     *                        integration as {@code <CLASSIFICATION>_<reportName>_<YYYYMMDD>.csv}.
     */
    public void exportCsv(String actor, Object datasourceInput, String outputDirectory,
                          ReportSecurityClassification classification, ReportSensitivity sensitivity)
            throws JRException, IOException {
        validator.validateActor(actor);
        validator.validateClassification(classification, sensitivity);

        // Construct filename — caller supplies only the directory; classification and date are not caller-supplied.
        String filename = classification.name()
                + "_" + jasperReport.getName()
                + "_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".csv";
        String outputPath = outputDirectory + "/" + filename;
        validator.validateFilePath(reportProperties.getOutputDirectoryPath(), outputPath);

        Files.createDirectories(Path.of(outputPath).getParent());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CLASSIFICATION", classification);
        parameters.put("SENSITIVITY",    sensitivity);
        JasperPrint print;
        try {
            print = fill(datasourceInput, parameters);
        } catch (JRException e) {
            log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
                "REPORT_FILL", "failure", jasperReport.getName(), "CSV", logSafeActor(actor), e);
            throw e;
        }

        File outFile = new File(outputPath);
        try (OutputStream out = new FileOutputStream(outFile)) {
            JRCsvExporter exporter = new JRCsvExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleWriterExporterOutput(out));
            exporter.exportReport();
        } catch (JRException | IOException e) {
            Files.deleteIfExists(outFile.toPath());
            log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
                "REPORT_EXPORT", "failure", jasperReport.getName(), "CSV", logSafeActor(actor), e);
            throw e;
        }

        log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_EXPORT", "success", jasperReport.getName(), "CSV", logSafeActor(actor));
    }

    /**
     * Human actors (from Spring Security) are hashed before logging.
     * System actors (uppercase prefix + colon, e.g. {@code BATCH_JOB:monthly-sales}) are logged as-is.
     */
    private static final java.util.regex.Pattern SYSTEM_ACTOR = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9_]*:.+");

    private String logSafeActor(String actor) {
        return SYSTEM_ACTOR.matcher(actor).matches() ? actor : hashUserId(actor);
    }

    /** Fills the report. The caller owns the ResultSet lifecycle — close it after this call returns. */
    private JasperPrint fill(Object datasourceInput, Map<String, Object> parameters) throws JRException {
        if (datasourceInput instanceof ResultSet rs) {
            return JasperFillManager.fillReport(jasperReport, parameters, new JRResultSetDataSource(rs));
        }
        return JasperFillManager.fillReport(jasperReport, parameters, datasourceFactory.build(datasourceInput));
    }
}
```

**Exception mapping**: Map `ReportValidationException` and `InvalidFilePathException` to `400 Bad Request`, `JRException` to `500`, and `IOException` to `500` using a `@RestControllerAdvice`. See the Spring Web exception handling guide for the standard `ProblemDetail` pattern.

**Actor in calling code**: The export service does not resolve the actor itself. Callers are responsible for supplying it:
```java
// User-initiated (REST controller)
String actor = SecurityContextHolder.getContext().getAuthentication().getName();
reportExportService.exportPdf(actor, datasource, outputPath, classification, sensitivity);

// System-generated (batch job)
reportExportService.exportPdf("BATCH_JOB:monthly-sales", datasource, outputPath, classification, sensitivity);
```

**One service bean per report type**: Register one `ReportExportService` bean per report type — each holding its own compiled `JasperReport`. This is the intended pattern. An application with five report types registers five beans. Each bean compiles its own template at startup, holds it privately, and exposes export methods for that template only. There is no shared mutable state between beans; concurrent fill calls against the same bean are safe because the compiled `JasperReport` is immutable and thread-safe. Do not attempt to make a single generic bean serve multiple templates by accepting the template as a parameter — that pattern reintroduces shared state and bypasses the startup compilation guarantee.

**Notes**:
*   Classification and sensitivity are injected as fill parameters (`CLASSIFICATION`, `SENSITIVITY`) into the JRXML template at fill time. If the classification is instead embedded as static text directly in the template, these keys can be omitted from the fill map.
*   For programmatic design, pre-compilation input validation must also run before passing the compiled report in — see `Report_Programmatic_Design_Recipes.md`.

---

## Recipe 9: Frontend Download — Streaming to HTTP Response

**Goal**: Stream a report directly to the HTTP response `OutputStream` without writing to disk. Use this for frontend download endpoints where a browser triggers a `GET /reports/download` and expects a file attachment.

Three behaviours differ from file export (Recipe 8):

1. **No `outputPath`** — `validateFilePath()` is not called; no output directory is created.
2. **Fill before opening the stream** — `JasperPrint` must be produced before calling `response.getOutputStream()`. If fill throws, the response is still uncommitted and Spring's `@ExceptionHandler` can still return a well-formed error body. If headers are written first and fill subsequently throws, the browser receives a corrupt document header followed by an error body.
3. **No partial file cleanup** — no file is created, so there is nothing to delete on failure. If export fails mid-write the response is already partially committed and cannot be rolled back — log at `ERROR` and rethrow.

### Add streaming methods to `ReportExportService`

```java
/**
 * Stream a PDF report directly to the HTTP response without writing to disk.
 *
 * @param actor    The identity initiating the export. See {@link #exportPdf} for actor conventions.
 * @param response The current {@link HttpServletResponse}. Must not be null or already committed.
 */
public void streamPdf(String actor, Object datasourceInput,
                      ReportSecurityClassification classification,
                      ReportSensitivity sensitivity,
                      HttpServletResponse response)
        throws JRException, IOException {

    // Pre-fill validation — no outputPath, so validateFilePath() is not called here
    validator.validateActor(actor);
    validator.validateClassification(classification, sensitivity);
    if (datasourceInput == null) throw new ReportValidationException("Datasource must not be null.");

    // Fill before touching the response. If fill throws, the response is still uncommitted
    // and Spring's exception handler can return a proper error response to the browser.
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("CLASSIFICATION", classification);
    parameters.put("SENSITIVITY",    sensitivity);
    JasperPrint print;
    try {
        print = fill(datasourceInput, parameters);
    } catch (JRException e) {
        log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_FILL", "failure", jasperReport.getName(), "PDF", logSafeActor(actor), e);
        throw e;
    }

    // Set Content-Type and Content-Disposition before calling response.getOutputStream().
    // Once the first byte is written the response is committed and headers are final.
    String filename = jasperReport.getName().replaceAll("[^\\w\\-]", "_") + ".pdf";
    response.setContentType("application/pdf");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

    // Export to the response stream. No file exists to clean up on failure.
    // If export fails after the response is committed, log and rethrow — the browser
    // will receive a truncated download; no further error response can be sent.
    try {
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(response.getOutputStream()));
        exporter.exportReport();
    } catch (JRException | IOException e) {
        log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_EXPORT", "failure", jasperReport.getName(), "PDF", logSafeActor(actor), e);
        throw e;
    }

    log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
        "REPORT_EXPORT", "success", jasperReport.getName(), "PDF", logSafeActor(actor));
}

/** XLSX variant — same ordering rules apply; Content-Type differs. */
public void streamXlsx(String actor, Object datasourceInput,
                       ReportSecurityClassification classification,
                       ReportSensitivity sensitivity,
                       HttpServletResponse response)
        throws JRException, IOException {

    validator.validateActor(actor);
    validator.validateClassification(classification, sensitivity);
    if (datasourceInput == null) throw new ReportValidationException("Datasource must not be null.");

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("CLASSIFICATION", classification);
    parameters.put("SENSITIVITY",    sensitivity);
    JasperPrint print;
    try {
        print = fill(datasourceInput, parameters);
    } catch (JRException e) {
        log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_FILL", "failure", jasperReport.getName(), "XLSX", logSafeActor(actor), e);
        throw e;
    }

    String filename = jasperReport.getName().replaceAll("[^\\w\\-]", "_") + ".xlsx";
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

    try {
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(response.getOutputStream()));
        exporter.exportReport();
    } catch (JRException | IOException e) {
        log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_EXPORT", "failure", jasperReport.getName(), "XLSX", logSafeActor(actor), e);
        throw e;
    }

    log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
        "REPORT_EXPORT", "success", jasperReport.getName(), "XLSX", logSafeActor(actor));
}

/** CSV variant — classification is encoded in the Content-Disposition filename per the CSV filename convention. */
public void streamCsv(String actor, Object datasourceInput,
                      ReportSecurityClassification classification,
                      ReportSensitivity sensitivity,
                      HttpServletResponse response)
        throws JRException, IOException {

    validator.validateActor(actor);
    validator.validateClassification(classification, sensitivity);
    if (datasourceInput == null) throw new ReportValidationException("Datasource must not be null.");

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("CLASSIFICATION", classification);
    parameters.put("SENSITIVITY",    sensitivity);
    JasperPrint print;
    try {
        print = fill(datasourceInput, parameters);
    } catch (JRException e) {
        log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_FILL", "failure", jasperReport.getName(), "CSV", logSafeActor(actor), e);
        throw e;
    }

    // Filename encodes classification and date — same convention as disk-based CSV export (Section 3.4).
    String filename = classification.name()
            + "_" + jasperReport.getName().replaceAll("[^\\w\\-]", "_")
            + "_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + ".csv";
    response.setContentType("text/csv");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

    try {
        JRCsvExporter exporter = new JRCsvExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(response.getOutputStream()));
        exporter.exportReport();
    } catch (JRException | IOException e) {
        log.error("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
            "REPORT_EXPORT", "failure", jasperReport.getName(), "CSV", logSafeActor(actor), e);
        throw e;
    }

    log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
        "REPORT_EXPORT", "success", jasperReport.getName(), "CSV", logSafeActor(actor));
}
```

### Wiring — REST controller

```java
@RestController
@RequestMapping("/reports")
public class ReportDownloadController {

    private final ReportExportService reportExportService;

    // Constructor injection omitted for brevity

    @GetMapping(value = "/download", produces = "application/pdf")
    public void downloadPdf(HttpServletResponse response) throws JRException, IOException {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        // Resolve datasource, classification, and sensitivity from the request/service layer
        reportExportService.streamPdf(actor, datasource, classification, sensitivity, response);
        // Do not return ResponseEntity — headers are set directly on the response by streamPdf().
    }
}
```

**Notes**:
*   `validateFilePath()` is not called — there is no output path to validate. The pre-fill validation branch for streaming skips path and directory checks entirely.
*   Fill must complete before `response.getOutputStream()` is called. This is the critical ordering difference from the file path. A fill failure before the response is touched allows Spring's `@ExceptionHandler` to write a proper `500` JSON body. A fill failure after headers are set produces a corrupt partial response.
*   Once `response.getOutputStream()` has been written to, the response is committed. Any exception thrown during export can be logged and rethrown, but the HTTP status and headers can no longer be changed. The browser will receive a truncated file. Surface these errors to APM.
*   Do not call `response.flushBuffer()` or `response.getWriter()` before calling a `stream*` method — a pre-flushed response is already committed and `setContentType()` / `setHeader()` calls will be silently ignored.
*   The classification is not part of the PDF or XLSX filename in the streaming path — it is rendered inside the document per the security contract. For CSV, the classification-encoded filename convention is preserved in the `Content-Disposition` header.

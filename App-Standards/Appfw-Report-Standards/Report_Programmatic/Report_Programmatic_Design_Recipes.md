# Report Generation — Programmatic JasperDesign Augmentation

> **Augmentation** — this document extends `Report_Core_Recipes.md`. It covers building a `JasperReport` entirely in code, without a `.jrxml` file. Datasource factory (base Recipe 5), file path validation (base Recipe 4), output directory (base Recipe 6), fill and export (base Recipe 7), and the export service (base Recipe 8) are shared with the base. Supporting types and pre-compilation validation specific to programmatic design are defined here.

---

## Recipe 1: Define Supporting Types for Programmatic Design

**Goal**: Define `ReportColumn` — the column metadata type used to drive both `JasperDesign` field declarations and the pre-compilation validation gate. The classification enums are shared with the base (base Recipe 2).

```java
public class ReportColumn {
    private final String columnName;   // Must match datasource field name
    private final String headerText;   // Display label in column header
    private final Class<?> valueClass;
    private final int width;           // Points; default 150

    public ReportColumn(String columnName, String headerText, Class<?> valueClass, int width) {
        this.columnName  = columnName;
        this.headerText  = headerText;
        this.valueClass  = valueClass;
        this.width       = width;
    }

    public String   getColumnName()  { return columnName; }
    public String   getHeaderText()  { return headerText; }
    public Class<?> getValueClass()  { return valueClass; }
    public int      getWidth()       { return width; }
}
```

---

## Recipe 2: Pre-Compilation Input Validation

**Goal**: Validate all caller-supplied inputs before building any `JasperDesign`. This gate is specific to programmatic design — JRXML templates define their own layout, so column and datasource validation at this point is not applicable there. Validation failures throw `ReportDesignValidationException` — mapped to `500 Internal Server Error` per the programmatic design error contract.

```java
public class ReportValidator {

    private static final int USABLE_PAGE_WIDTH = 555; // A4 (595 pt) minus 20 pt left and right margins

    /** Pre-compilation: validate caller-supplied inputs before building a JasperDesign. */
    public void validateInputs(String reportName,
                               ReportSecurityClassification classification,
                               ReportSensitivity sensitivity,
                               Object datasource,
                               List<ReportColumn> columns) {
        if (reportName == null || reportName.isBlank()) {
            throw new ReportDesignValidationException("Report name must not be null or blank.");
        }
        if (classification == null) {
            throw new ReportDesignValidationException("Security classification is required.");
        }
        if (sensitivity == null) {
            throw new ReportDesignValidationException("Sensitivity level is required.");
        }
        if (datasource == null) {
            throw new ReportDesignValidationException("Datasource must not be null.");
        }
        validateColumns(columns);
    }

    /** Validates column descriptors. Called internally by {@link #validateInputs}. */
    public void validateColumns(List<ReportColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new ReportDesignValidationException("At least one column must be defined.");
        }
        Set<String> seen = new HashSet<>();
        int totalWidth = 0;
        for (ReportColumn col : columns) {
            if (col.getColumnName() == null || col.getColumnName().isBlank()) {
                throw new ReportDesignValidationException("Column name must not be null or blank.");
            }
            if (col.getColumnName().contains("$P{") || col.getColumnName().contains("$V{") || col.getColumnName().contains("$F{")) {
                throw new ReportDesignValidationException("Column name contains a disallowed expression token: " + col.getColumnName());
            }
            if (!seen.add(col.getColumnName())) {
                throw new ReportDesignValidationException("Duplicate column name: " + col.getColumnName());
            }
            if (col.getHeaderText() == null || col.getHeaderText().isBlank()) {
                throw new ReportDesignValidationException("Header text must not be null or blank for column: " + col.getColumnName());
            }
            if (col.getValueClass() == null) {
                throw new ReportDesignValidationException("Value class must not be null for column: " + col.getColumnName());
            }
            if (col.getWidth() <= 0) {
                throw new ReportDesignValidationException("Column width must be greater than zero for column: " + col.getColumnName());
            }
            totalWidth += col.getWidth();
        }
        if (totalWidth > USABLE_PAGE_WIDTH) {
            throw new ReportDesignValidationException(
                "Total column width " + totalWidth + " pt exceeds usable page width of " + USABLE_PAGE_WIDTH + " pt.");
        }
    }
}
```

**Order of operations**: Call `validateInputs()` and `validateFilePath()` (base Recipe 4) before building the `JasperDesign`, creating directories, or filling. No `JasperDesign` should be built from invalid inputs.

---

## Recipe 3: Build a JasperReport Programmatically

**Goal**: Build a `JasperDesign` from your own inputs and compile it to a `JasperReport`. Use this when no `.jrxml` template exists — layout, fields, bands, and classification tags are all constructed in code.

```java
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;

public class JasperDesignBuilder {

    private static final int PAGE_WIDTH    = 595;
    private static final int PAGE_HEIGHT   = 842;
    private static final int MARGIN        = 20;
    private static final int ROW_HEIGHT    = 20;

    public JasperReport build(String reportName,
                              ReportSecurityClassification classification,
                              ReportSensitivity sensitivity,
                              List<ReportColumn> columns,
                              String titleText) throws JRException {
        JasperDesign jd = new JasperDesign();
        jd.setName(reportName);
        jd.setPageWidth(PAGE_WIDTH);
        jd.setPageHeight(PAGE_HEIGHT);
        jd.setLeftMargin(MARGIN);
        jd.setRightMargin(MARGIN);
        jd.setTopMargin(MARGIN);
        jd.setBottomMargin(MARGIN);

        int contentWidth = PAGE_WIDTH - MARGIN * 2;

        // Declare fields — one per column, matching datasource field names
        for (ReportColumn col : columns) {
            JRDesignField field = new JRDesignField();
            field.setName(col.getColumnName());
            field.setValueClass(col.getValueClass());
            jd.addField(field);
        }

        // Title band
        if (titleText != null) {
            JRDesignBand titleBand = new JRDesignBand();
            titleBand.setHeight(40);
            titleBand.addElement(staticText(titleText, 0, 0, contentWidth, 40, true, 18, HorizontalTextAlignEnum.CENTER));
            jd.setTitle(titleBand);
        }

        // Classification tag in page header and footer (required for PDF)
        String tag = classificationTag(classification, sensitivity);
        jd.setPageHeader(classificationBand(tag, contentWidth));
        jd.setPageFooter(classificationBand(tag, contentWidth));

        // Column header band
        JRDesignBand colHeader = new JRDesignBand();
        colHeader.setHeight(ROW_HEIGHT);
        int x = 0;
        for (ReportColumn col : columns) {
            colHeader.addElement(staticText(col.getHeaderText(), x, 0, col.getWidth(), ROW_HEIGHT, true, 10, HorizontalTextAlignEnum.LEFT));
            x += col.getWidth();
        }
        jd.setColumnHeader(colHeader);

        // Detail band — one text field per column, referencing $F{columnName}
        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(ROW_HEIGHT);
        x = 0;
        for (ReportColumn col : columns) {
            JRDesignTextField tf = new JRDesignTextField();
            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{" + col.getColumnName() + "}");
            tf.setExpression(expr);
            tf.setX(x); tf.setY(0);
            tf.setWidth(col.getWidth()); tf.setHeight(ROW_HEIGHT);
            detail.addElement(tf);
            x += col.getWidth();
        }
        ((JRDesignSection) jd.getDetailSection()).addBand(detail);

        return JasperCompileManager.compileReport(jd);
    }

    private JRDesignBand classificationBand(String tag, int width) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(ROW_HEIGHT);
        band.addElement(staticText(tag, 0, 0, width, ROW_HEIGHT, true, 9, HorizontalTextAlignEnum.CENTER));
        return band;
    }

    private JRDesignStaticText staticText(String text, int x, int y, int width, int height,
                                          boolean bold, int fontSize, HorizontalTextAlignEnum align) {
        JRDesignStaticText st = new JRDesignStaticText();
        st.setText(text);
        st.setX(x); st.setY(y);
        st.setWidth(width); st.setHeight(height);
        st.setBold(bold);
        st.setFontSize((float) fontSize);
        st.setHorizontalTextAlign(align);
        return st;
    }

    private String classificationTag(ReportSecurityClassification c, ReportSensitivity s) {
        // Construct the display label from classification + sensitivity
        // Exact format is an org decision — this is one example
        return c.name().replace("_", " ") + " — " + s.name().replace("_", " ");
    }
}
```

---

## Recipe 4: Using the Builder — End-to-End Example

**Goal**: Wire `JasperDesignBuilder` into the full validation and export flow, starting from defining all inputs.

```java
// 0. Define all inputs

// Report identity and security
String reportName      = "Monthly Staff Report";
String titleText       = "Staff Report — April 2026";
String outputPath      = "staff/monthly-staff-report.pdf";

ReportSecurityClassification classification = ReportSecurityClassification.OFFICIAL_CLOSED;
ReportSensitivity            sensitivity    = ReportSensitivity.SENSITIVE_NORMAL;

// Resolve user identity from Spring Security context
String userId = SecurityContextHolder.getContext().getAuthentication().getName();

// Column definitions — columnName must match field names in the datasource exactly
List<ReportColumn> columns = List.of(
    new ReportColumn("firstName", "First Name", String.class,     150),
    new ReportColumn("lastName",  "Last Name",  String.class,     150),
    new ReportColumn("amount",    "Amount",     BigDecimal.class, 100)
);

// Datasource — a Map where each key matches a columnName and each value list is one entry per row
Map<String, List<Object>> datasourceInput = Map.of(
    "firstName", List.of("Alice", "Bob",   "Carol"),
    "lastName",  List.of("Smith", "Jones", "White"),
    "amount",    List.of(new BigDecimal("1200.00"),
                         new BigDecimal("850.50"),
                         new BigDecimal("2340.75"))
);

// 1. Validate inputs before building anything (Recipe 2 + base Recipe 4)
validator.validateInputs(reportName, classification, sensitivity, datasourceInput, columns);
validator.validateFilePath(reportProperties.getOutputDirectoryPath(), outputPath);
validator.validateUserId(userId);

// 2. Build and compile the JasperDesign (Recipe 3)
JasperReport compiled = new JasperDesignBuilder()
        .build(reportName, classification, sensitivity, columns, titleText);

// 3. Validate the compiled report (base Recipe 3)
validator.validate(compiled);

// 4. Convert datasource input and fill with data
JRDataSource datasource = datasourceFactory.build(datasourceInput);
JasperPrint print = JasperFillManager.fillReport(compiled, new HashMap<>(), datasource);

// 5. Export to PDF (base Recipe 8 — or inline for one-off use)
try (OutputStream out = new FileOutputStream(outputPath)) {
    JRPdfExporter exporter = new JRPdfExporter();
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
    exporter.exportReport();
}
log.info("event.action={} event.outcome={} file.name={} file.extension={} user.id={}",
    "REPORT_EXPORT", "success", reportName, "PDF", hashUserId(userId));
```

**Integration notes**:

*   `validateInputs()` must run before `JasperDesignBuilder.build()` — the builder does not validate its inputs.
*   `columnName` in each `ReportColumn` must exactly match the key in the datasource map (or the field name in the `ResultSet` / CSV header). A mismatch causes a fill-time error.
*   The builder produces an A4-portrait layout (595 × 842 pt). Override `PAGE_WIDTH` / `PAGE_HEIGHT` for landscape or other sizes.
*   Column widths are caller-supplied via `ReportColumn.getWidth()`. Ensure the sum of column widths does not exceed `PAGE_WIDTH - 2 * MARGIN` (555 pt at default margins) or elements will overflow the page.

---

## Recipe 5: Adding Paragraph Content to a JasperDesign

Paragraphs are free-text elements placed in non-detail bands (title, summary, group header/footer). They are not row-scoped and must not go in the detail band.

### 5a. Static Paragraph (trusted constant text)

Use `JRDesignStaticText` for fixed text such as disclaimers or report metadata. Static text is not evaluated as a JasperReports expression.

```java
JRDesignBand summaryBand = new JRDesignBand();
summaryBand.setHeight(50);

JRDesignStaticText para = new JRDesignStaticText();
para.setX(0);
para.setY(0);
para.setWidth(555);   // full usable width at default A4 margins
para.setHeight(40);
para.setText("This report contains confidential financial data. Do not distribute.");
para.setStretchType(StretchTypeEnum.ELEMENT_GROUP_HEIGHT);

summaryBand.addElement(para);
jasperDesign.setSummary(summaryBand);
```

### 5b. Dynamic Paragraph (caller-supplied text via fill parameter)

Use `JRDesignTextField` with a `$P{}` expression for text that varies per request. Declare the parameter on the `JasperDesign`, then supply it in the fill parameters map at fill time.

```java
// 1. Declare the parameter on the design (before compilation)
JRDesignParameter param = new JRDesignParameter();
param.setName("REPORT_SUMMARY");
param.setValueClass(String.class);
jasperDesign.addParameter(param);

// 2. Add the text field to the summary band
JRDesignBand summaryBand = new JRDesignBand();
summaryBand.setHeight(60);

JRDesignTextField para = new JRDesignTextField();
para.setX(0);
para.setY(0);
para.setWidth(555);
para.setHeight(50);
para.setStretchWithOverflow(true);  // expands if text exceeds fixed height
para.setExpression(new JRDesignExpression("$P{REPORT_SUMMARY}"));

summaryBand.addElement(para);
jasperDesign.setSummary(summaryBand);

// 3. At fill time, supply the parameter value
// Validate summaryText before this point — reject any string containing $P{, $V{, or $F{
Map<String, Object> params = new HashMap<>();
params.put("REPORT_SUMMARY", summaryText);
JasperPrint print = JasperFillManager.fillReport(jasperReport, params, datasource);
```

**Notes**:

*   Validate all caller-supplied string inputs before use — both column names and paragraph text. Reject strings containing `$P{`, `$V{`, or `$F{`.
*   `setStretchWithOverflow(true)` is required for multi-line text — without it, text is clipped at the fixed element height.
*   `$P{}` is the correct expression type for paragraph content. `$F{}` must not be used — datasource fields are row-scoped and repeat with every detail row.
*   The summary band renders once, after all detail rows. For a pre-table paragraph, use the title band (`jasperDesign.setTitle(...)`) with the same element construction.


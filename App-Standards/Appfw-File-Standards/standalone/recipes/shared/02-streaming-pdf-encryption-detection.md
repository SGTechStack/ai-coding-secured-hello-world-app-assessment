# 02. PDF Encryption Detection

**Goal**: Detect if a PDF is encrypted before allowing storage.


**Java Implementation (PDFBox 3.x — updated API)**:

```java
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;

@Component
public class PdfEncryptionDetector {

    /**
     * Detect PDF encryption from the pre-validated byte array.
     * RandomAccessReadBuffer wraps the existing array in-place — no extra allocation.
     *
     * @param bytes the PDF content, already size-checked
     * @return true if the PDF is encrypted
     * @throws FileValidationException on IOException (must propagate, not swallow)
     */
    public boolean isEncrypted(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            return doc.isEncrypted();
        } catch (InvalidPasswordException e) {
            // Password-protected → encrypted
            return true;
        } catch (IOException e) {
            // MUST reject and propagate — never swallow
            log.error("[PDF_VALIDATION_FAIL] IOException during PDF encryption check", e);
            throw new FileValidationException(
                "Failed to parse PDF for encryption check: " + e.getMessage(), e);
        }
    }
}
```

> **Note**: PDFBox requires random access to navigate the PDF cross-reference table, so it cannot do a single-pass forward read regardless of input type. `new RandomAccessReadBuffer(byte[])` wraps the existing array without copying; `createBufferFromStream(InputStream)` would read the entire stream into a new buffer. Since the byte array is already materialized upstream, use the array constructor directly.


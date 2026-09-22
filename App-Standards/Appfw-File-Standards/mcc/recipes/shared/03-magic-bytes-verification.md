# 03. Magic Bytes Verification

**Goal**: Verify file type by content signature, not extension. Prevents extension spoofing (e.g., renaming `.exe` to `.pdf`).


**Registry**:

| MIME Type | Signature Name | Byte Sequence (Hex) |
|:---|:---|:---|
| `application/pdf` | PDF | `25 50 44 46 2D` (`%PDF-`) |
| `image/png` | PNG | `89 50 4E 47 0D 0A 1A 0A` |
| `image/jpeg` | JPEG Variant 1 | `FF D8 FF DB` |
| `image/jpeg` | JPEG Variant 2 | `FF D8 FF E0` |
| `image/jpeg` | JPEG Variant 3 | `FF D8 FF E1` |
| `image/jpeg` | JPEG Variant 4 | `FF D8 FF E2` |
| `image/jpeg` | JPEG Variant 5 | `FF D8 FF EE` |
| OOXML (`xlsx`, `docx`, `pptx`) | PK (ZIP) | `50 4B 03 04` |

**Java Implementation (JDK 21 HexFormat)**:

```java
import java.util.HexFormat;

public void validateMagicBytes(byte[] bytes, String expectedMimeType) {
    List<byte[]> signatures = MagicBytesRegistry.get(expectedMimeType);
    if (signatures == null || signatures.isEmpty()) return; // No registered signature

    boolean matched = signatures.stream()
        .anyMatch(sig -> bytes.length >= sig.length
            && MessageDigest.isEqual(sig, Arrays.copyOf(bytes, sig.length)));

    if (!matched) {
        throw new FileValidationException("MAGIC_BYTES mismatch for " + expectedMimeType);
    }
}
```

> **Fix**: All 5 JPEG variants are included (legacy only had 1).


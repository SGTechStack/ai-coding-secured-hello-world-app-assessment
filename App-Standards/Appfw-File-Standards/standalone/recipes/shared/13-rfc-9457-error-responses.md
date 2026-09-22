# 13. RFC 9457 Error Responses

**Goal**: Standardized error payloads using Spring 6+ `ProblemDetail`.


**Java Implementation**:

```java
@RestControllerAdvice
public class FileUploadExceptionHandler {

    @ExceptionHandler(FileValidationException.class)
    public ProblemDetail handleValidation(FileValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Validation Failed");
        pd.setType(URI.create("https://api.example.com/errors/validation-error"));
        return pd;
    }

    @ExceptionHandler(StorageQuotaExceededException.class)
    public ProblemDetail handleQuota(StorageQuotaExceededException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INSUFFICIENT_STORAGE, ex.getMessage());  // 507
    }

    @ExceptionHandler(FileNotReadyException.class)
    public ProblemDetail handleNotReady(FileNotReadyException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, ex.getMessage());  // 403
        pd.setTitle("File Not Available");
        return pd;
    }

    @ExceptionHandler(SfsUploadException.class)
    public ProblemDetail handleSfs(SfsUploadException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY, ex.getMessage());  // 502
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());  // 404
    }

    @ExceptionHandler(RowParsingException.class)
    public ProblemDetail handleRowParsing(RowParsingException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()); // 422
    }
}
```

**RFC 9457 Response Shape**:

```json
{
  "type": "https://api.example.com/errors/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "File size exceeds maximum (10485760 bytes)",
  "instance": "/upload",
  "traceId": "4e9e48b4-3a7e-4c9d-8e8e-5f5e5e5e5e5e"
}
```

**HTTP Status Code Mappings**:

| Status | Scenario |
|:---|:---|
| `200 OK` | File upload succeeds |
| `400 Bad Request` | Validation failure (size, MIME, magic bytes, encrypted PDF, display name) |
| `403 Forbidden` | Attempt to download a file not in retained `DOWNLOADED` state |
| `404 Not Found` | File not found during download or poll |
| `422 Unprocessable Entity` | Row parsing/processing failure during ingestion |
| `502 Bad Gateway` | SFS communication error |
| `507 Insufficient Storage` | Storage quota exceeded (standalone) |
| `500 Internal Server Error` | Unexpected system error |


# 17. AWS Scanner Bypass Mode (Local Development)

**Goal**: Support local development without external scanner connectivity.


**Activation**: Set `file.scanner.enabled=false`.

```java
@Configuration
@Profile("aws")
public class ExternalScannerBypassConfig {

    @Bean
    @ConditionalOnProperty(name = "file.scanner.enabled", havingValue = "true", matchIfMissing = true)
    public ScanWorkflow realScanWorkflow(/* profile-specific dependencies */) {
        return /* AwsScanWorkflow */; // Real external scanner path
    }

    @Bean
    @ConditionalOnProperty(name = "file.scanner.enabled", havingValue = "false")
    public ScanWorkflow bypassScanWorkflow(BlobStorage blobStorage) {
        return new AwsScannerBypassWorkflow(blobStorage); // AWS development bypass mode
    }
}
```

**Behavior Comparison**:

| Scenario | Batch Jobs | Scanner | File Transition |
|:---|:---|:---|:---|
| **Production (enabled=true)** | Run | SFS | PENDING_SCAN -> ...phases... -> DOWNLOADED |
| **AWS development bypass (enabled=false)** | Run | Bypassed | PENDING_SCAN -> DOWNLOADED (immediate) |
| **Standalone Profile** | N/A | N/A | PENDING_SCAN -> DOWNLOADED (immediate) |

> **Note**: Batch jobs still run in bypass mode — the scheduler is not disabled. The bypass workflow simply promotes files locally instead of calling the external scanner, so the batch cycle completes immediately for each file. The final artifact is still written through the configured clean-store backend for that profile.
> Standalone does not use this bypass mode; local promotion is the profile's normal lifecycle, not an AWS development toggle.


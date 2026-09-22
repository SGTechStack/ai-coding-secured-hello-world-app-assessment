# 14. Hibernate 7 LOB Strategy

**Goal**: Use bytecode enhancement for lazy loading of large binary objects.


**Java Implementation**:

```java
@Entity
@Table(name = "FILE_CONTENT_DIRTY", indexes = {
    @Index(name = "idx_fcd_metadata", columnList = "file_metadata_id")
})
public class DirtyFileBlob {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.BLOB)  // Hibernate 7 — replaces @Lob
    @Basic(fetch = FetchType.LAZY) // Requires bytecode enhancement (see Maven plugin below)
    @Column(name = "blob", nullable = false)
    private java.sql.Blob blob;

    @Column(name = "file_metadata_id", nullable = false)
    private UUID fileMetadataId;
}

@Entity
@Table(name = "FILE_CONTENT_CLEANED")
public class CleanFileBlob {

    @Id
    @Column(name = "id")
    private UUID id; // Matches FileMetadata.id — shared PK

    @JdbcTypeCode(SqlTypes.BLOB)  // Hibernate 7 — replaces @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "blob", nullable = false)
    private java.sql.Blob blob;
}
```

> **Anti-Pattern**: `@Lob` + `byte[]` is the Hibernate 6 / Spring Boot 3 approach. In **Hibernate 7**, use `@JdbcTypeCode(SqlTypes.BLOB)` + `java.sql.Blob` for explicit dialect-aware BLOB mapping. `@Lob` on `byte[]` is deprecated and behaves inconsistently across MSSQL and Oracle.

**Bytecode Enhancement Required** (`pom.xml` — `hibernate-maven-plugin`):

```xml
<plugin>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-maven-plugin</artifactId>
    <version>${hibernate.version}</version>
    <executions>
        <execution>
            <id>enhance</id>
            <goals>
                <goal>enhance</goal>
            </goals>
            <configuration>
                <enableLazyInitialization>true</enableLazyInitialization>
                <enableDirtyTracking>true</enableDirtyTracking>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **How it works**: The plugin weaves bytecode at compile time, enabling `@Basic(fetch = FetchType.LAZY)` on non-association fields (like BLOBs). Without this plugin, `FetchType.LAZY` on BLOB fields is silently ignored and the blob is always eagerly loaded.

**RDBMS Compatibility**:

| Feature | MSSQL 2019+ | Oracle 19c+ |
|:---|:---|:---|
| UUID PK | `UNIQUEIDENTIFIER` | `RAW(16)` via Hibernate |
| LOB (`byte[]`) | `VARBINARY(MAX)` | `BLOB` |
| `PESSIMISTIC_WRITE` | `WITH (UPDLOCK, ROWLOCK)` | `FOR UPDATE` |


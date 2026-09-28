package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * V7 is Spring Session JDBC 4.1.1's {@code schema-h2.sql}, copied verbatim under a comment header (REJ-040). The
 * body's git blob hash pins it byte for byte, so a vendor upgrade, a hand edit, CRLF or a BOM fails here.
 */
class SpringSessionSchemaTest {

    private static final String V7 = "db/migration/V7__spring_session.sql";

    /** {@code git hash-object} of spring-session-jdbc 4.1.1's {@code org/springframework/session/jdbc/schema-h2.sql}. */
    private static final String UPSTREAM_BLOB_HASH = "be6e515720a5434a898bcb6d186f42d7b4766006";

    @Test
    @Proves("T-BLD-001")
    void v7IsAVerbatimCopyOfTheSpringSessionH2Schema() throws IOException {
        byte[] body = stripHeader(readV7());

        assertThat(new String(body, StandardCharsets.UTF_8)).startsWith("CREATE TABLE SPRING_SESSION");
        assertThat(gitBlobHash(body)).isEqualTo(UPSTREAM_BLOB_HASH);
    }

    @Test
    void anyChangeToTheSessionDdlChangesTheHash() throws IOException {
        byte[] body = stripHeader(readV7());
        byte[] crlf = new String(body, StandardCharsets.UTF_8).replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] edited = new String(body, StandardCharsets.UTF_8).replace("VARCHAR(100)", "VARCHAR(200)")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(gitBlobHash(crlf)).isNotEqualTo(UPSTREAM_BLOB_HASH);
        assertThat(gitBlobHash(edited)).isNotEqualTo(UPSTREAM_BLOB_HASH);
    }

    private static byte[] readV7() throws IOException {
        try (InputStream in = SpringSessionSchemaTest.class.getClassLoader().getResourceAsStream(V7)) {
            assertThat(in).as(V7).isNotNull();
            return in.readAllBytes();
        }
    }

    /** Drops the leading run of {@code --} lines and the one blank line after it. */
    private static byte[] stripHeader(byte[] file) {
        int offset = 0;
        while (startsWith(file, offset, "--")) {
            offset = nextLine(file, offset);
        }
        assertThat(offset).as("V7 has a comment header").isPositive();
        assertThat(file[offset]).as("one blank line after the header").isEqualTo((byte) '\n');
        return Arrays.copyOfRange(file, offset + 1, file.length);
    }

    private static boolean startsWith(byte[] file, int offset, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        return file.length >= offset + expected.length
                && Arrays.equals(file, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static int nextLine(byte[] file, int offset) {
        int newline = offset;
        while (file[newline] != '\n') {
            newline++;
        }
        return newline + 1;
    }

    /** {@code SHA1("blob " + length + "\0" + bytes)}, as {@code git hash-object} computes it. */
    static String gitBlobHash(byte[] content) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(sha1.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

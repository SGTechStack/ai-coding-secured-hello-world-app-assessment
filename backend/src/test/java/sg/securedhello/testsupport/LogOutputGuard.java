package sg.securedhello.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import sg.securedhello.audit.AuditEmitter;

/**
 * The suite-wide output guard, registered for every test class through JUnit's extension auto-detection
 * ({@code junit-platform.properties} and {@code META-INF/services}). It tees stdout and stderr, which every appender
 * but the audit file writes to, and reads the audit file as it grows. After each test, and after each class, it
 * scans everything written since the last scan and fails the test when it finds:
 * <ul>
 *   <li>a <em>canary secret</em> in any encoded form: every {@link TestSecrets#CANARIES} value, and any server
 *       secret a test {@linkplain #register registered} (T-AUD-013);</li>
 *   <li>a degraded audit row, unless the test is annotated {@link ExpectsDegradedAuditRow}. This is what makes the
 *       emitter's fail-soft path fail hard at test time (ADR-055).</li>
 * </ul>
 * Output written while a Spring context starts is scanned with the first test that uses it.
 */
public final class LogOutputGuard implements AfterEachCallback, AfterAllCallback {

    /** Where the tests' audit file lives: {@code app.audit.directory}, set by Surefire and Failsafe. */
    static final String AUDIT_DIRECTORY_PROPERTY = "app.audit.directory";

    /** Characters kept from the end of each scan, so a secret split across two scans is still found. */
    private static final int OVERLAP = 512;

    private static final Capture CAPTURE = new Capture();
    private static final List<String> REGISTERED = new CopyOnWriteArrayList<>();

    private static String carried = "";
    private static long auditFileOffset;

    static {
        System.setOut(new PrintStream(new Tee(System.out, CAPTURE), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new Tee(System.err, CAPTURE), true, StandardCharsets.UTF_8));
    }

    /**
     * Registers a server secret a test captured, such as a session id or CSRF token, so it is scanned for until the
     * test ends. Values shorter than 8 characters would match by chance and are refused.
     */
    public static void register(String secret) {
        if (secret == null || secret.length() < 8) {
            throw new IllegalArgumentException("a registered secret must be at least 8 characters");
        }
        REGISTERED.add(secret);
    }

    /** True if what this thread prints to {@code System.out} and {@code System.err} reaches the guard. */
    public static boolean capturesStandardStreams() {
        String marker = "log-output-guard-probe-" + Long.toHexString(Double.doubleToLongBits(Math.random()));
        System.out.println(marker);
        System.err.println(marker + "-err");
        return CAPTURE.contains(marker) && CAPTURE.contains(marker + "-err");
    }

    /** Scans the output written since the last scan, as the guard does after each test, and returns the findings. */
    public static synchronized List<String> scanNow(boolean degradedRowExpected) {
        String text = carried + CAPTURE.drain() + auditFileGrowth();
        List<String> findings = new ArrayList<>(leaks(text, secrets()));
        if (!degradedRowExpected && text.contains(AuditEmitter.DEGRADED_MESSAGE)) {
            findings.add("a degraded audit row was written; the emitter's fail-soft path must be unreachable");
        }
        // What was reported, or expected, is not carried into the next scan.
        carried = findings.isEmpty()
                ? text.substring(Math.max(0, text.length() - OVERLAP)).replace(AuditEmitter.DEGRADED_MESSAGE, "")
                : "";
        return findings;
    }

    /** Which of {@code secrets} appear in {@code text}, in any of their {@linkplain #encodedForms encoded forms}. */
    public static List<String> leaks(String text, List<String> secrets) {
        List<String> found = new ArrayList<>();
        for (int i = 0; i < secrets.size(); i++) {
            for (String form : encodedForms(secrets.get(i))) {
                if (text.contains(form)) {
                    found.add("secret #" + i + " reached the output, as " + describe(secrets.get(i), form));
                    break;
                }
            }
        }
        return found;
    }

    /**
     * {@code secret} as it could reach a log line: verbatim, URL-encoded, JSON-escaped, Base64 (standard and URL-safe,
     * padded and not) and hex (both cases) of its UTF-8 bytes; and, if it is itself Base64, hex of the decoded bytes.
     */
    public static Set<String> encodedForms(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        Set<String> forms = new LinkedHashSet<>();
        forms.add(secret);
        forms.add(URLEncoder.encode(secret, StandardCharsets.UTF_8));
        forms.add(secret.replace("/", "\\/"));
        forms.add(Base64.getEncoder().encodeToString(bytes));
        forms.add(Base64.getEncoder().withoutPadding().encodeToString(bytes));
        forms.add(Base64.getUrlEncoder().encodeToString(bytes));
        forms.add(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        forms.add(HexFormat.of().formatHex(bytes));
        forms.add(HexFormat.of().withUpperCase().formatHex(bytes));
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            forms.add(HexFormat.of().formatHex(decoded));
            forms.add(HexFormat.of().withUpperCase().formatHex(decoded));
        } catch (IllegalArgumentException notBase64) {
            // Only the forms above apply.
        }
        return forms;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        REGISTERED.clear();
        failOn(scanNow(expectsDegradedRow(context)), context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        failOn(scanNow(expectsDegradedRow(context)), context);
    }

    private static boolean expectsDegradedRow(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> AnnotationSupport.isAnnotated(method, ExpectsDegradedAuditRow.class))
                .orElse(false)
                || context.getTestClass()
                .map(type -> AnnotationSupport.isAnnotated(type, ExpectsDegradedAuditRow.class))
                .orElse(false);
    }

    private static void failOn(List<String> findings, ExtensionContext context) {
        if (!findings.isEmpty()) {
            throw new AssertionError("LogOutputGuard (T-AUD-013) after " + context.getDisplayName() + ": "
                    + String.join("; ", findings));
        }
    }

    private static List<String> secrets() {
        List<String> secrets = new ArrayList<>(TestSecrets.CANARIES);
        secrets.addAll(REGISTERED);
        return secrets;
    }

    /** Names the encoding without echoing the secret, so the failure message does not itself leak it. */
    private static String describe(String secret, String form) {
        if (form.equals(secret)) {
            return "plain text";
        }
        return form.chars().allMatch(c -> Character.digit(c, 16) >= 0) ? "hex" : "an encoded form";
    }

    private static String auditFileGrowth() {
        String directory = System.getProperty(AUDIT_DIRECTORY_PROPERTY);
        if (directory == null) {
            return "";
        }
        Path file = Path.of(directory, "audit.ndjson");
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            if (in.length() < auditFileOffset) {
                auditFileOffset = 0;
            }
            byte[] grown = new byte[(int) (in.length() - auditFileOffset)];
            in.seek(auditFileOffset);
            in.readFully(grown);
            auditFileOffset += grown.length;
            return new String(grown, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the audit file " + file, e);
        }
    }

    /** Everything written to the teed streams since the last drain, as ISO-8859-1 so no byte is lost. */
    private static final class Capture extends OutputStream {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public synchronized void write(int b) {
            buffer.write(b);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            buffer.write(bytes, offset, length);
        }

        synchronized boolean contains(String text) {
            return buffer.toString(StandardCharsets.ISO_8859_1).contains(text);
        }

        synchronized String drain() {
            String text = buffer.toString(StandardCharsets.ISO_8859_1);
            buffer.reset();
            return text;
        }
    }

    /** Writes to the original stream and to the capture. */
    private static final class Tee extends OutputStream {

        private final OutputStream original;
        private final OutputStream capture;

        Tee(OutputStream original, OutputStream capture) {
            this.original = original;
            this.capture = capture;
        }

        @Override
        public void write(int b) throws IOException {
            original.write(b);
            capture.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            original.write(bytes, offset, length);
            capture.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            original.flush();
        }
    }
}

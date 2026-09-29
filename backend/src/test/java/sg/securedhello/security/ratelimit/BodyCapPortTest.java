package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.Proves;

import tools.jackson.databind.json.JsonMapper;

/**
 * The body cap counts bytes as they are read, through real Tomcat (level P), for bodies whose {@code Content-Length}
 * does not tell the truth (Std §5:494). The login is sent with a real session and token, so only the body can fail.
 */
class BodyCapPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private RequestBodyProperties requestBody;

    private String cookie;
    private String token;

    @BeforeEach
    void bootstrap() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/csrf")).build(),
                    BodyHandlers.ofString());
            cookie = response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            token = JSON.readTree(response.body()).get("token").asString();
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    /** A JSON login body of exactly {@code bytes} bytes. */
    private static byte[] body(long bytes) {
        String prefix = "{\"username\":\"" + Accounts.unknownUsername() + "\",\"password\":\"x\",\"pad\":\"";
        String suffix = "\"}";
        return (prefix + "a".repeat((int) (bytes - prefix.length() - suffix.length())) + suffix)
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @Proves("T-RL-013")
    void aChunkedBodyOverTheCapIsRefusedAsValidation() throws Exception {
        assertThat(chunkedLogin(body(requestBody.maxBodyBytes() + 1)))
                .startsWith("400").contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(chunkedLogin(body(requestBody.maxBodyBytes())))
                .as("a chunked body at the cap reaches the provider").startsWith("401");
    }

    @Test
    @Proves("T-RL-014")
    void anOverCapBodyWithAnUnderstatedLengthIsRefusedAsValidation() throws Exception {
        byte[] oversized = body(2 * requestBody.maxBodyBytes());

        // Declared under the cap: Tomcat reads only the declared bytes, which cut the JSON short.
        assertThat(rawLogin(oversized, 100)).startsWith("HTTP/1.1 400").contains("\"code\":\"VALIDATION_FAILED\"");
        // Declared over the cap, though still short of the truth: refused on the declared length.
        assertThat(rawLogin(oversized, requestBody.maxBodyBytes() + 1)).startsWith("HTTP/1.1 400")
                .contains("\"code\":\"VALIDATION_FAILED\"");
    }

    /** POSTs {@code body} chunked, with no {@code Content-Length}; returns the status and the body. */
    private String chunkedLogin(byte[] body) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/login"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", cookie)
                    .header(CsrfSession.HEADER, token)
                    .POST(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                    .build(), BodyHandlers.ofString());
            return response.statusCode() + " " + response.body();
        }
    }

    /** Writes the login by hand, declaring {@code declaredLength}; returns the raw response. */
    private String rawLogin(byte[] body, long declaredLength) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /api/login HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + declaredLength + "\r\n"
                    + "Cookie: " + cookie + "\r\n"
                    + CsrfSession.HEADER + ": " + token + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            try {
                out.write(body);
                out.flush();
            } catch (IOException closedEarly) {
                // The server may answer and close before reading everything; its answer is what is under test.
            }
            InputStream in = socket.getInputStream();
            return firstResponse(in);
        }
    }

    /** The first response on the connection, up to the end of its body or the connection. */
    private static String firstResponse(InputStream in) throws IOException {
        StringBuilder text = new StringBuilder();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) > 0) {
            text.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            if (text.indexOf("}") > 0 && text.indexOf("\r\n\r\n") > 0) {
                break;
            }
        }
        return text.toString();
    }
}

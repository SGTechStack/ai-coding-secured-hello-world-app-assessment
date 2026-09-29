package sg.securedhello.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import sg.securedhello.email.EmailService;
import sg.securedhello.email.LinkEmail;

import tools.jackson.databind.json.JsonMapper;

/**
 * The Playwright suite's mailbox: the {@link EmailService} of the e2e backend only, in the test sources, so nothing here
 * reaches a production build. It keeps every link it is sent and hands the latest one for an address to the browser
 * test at {@code GET /e2e/mailbox?to=<address>}, a servlet filter ahead of the application's own chain, so the suite
 * never scrapes a log for a token. Answers 404 until a link has been sent to that address.
 */
final class E2eMailbox implements EmailService, Filter {

    /** Where the browser tests read their mail. */
    static final String PATH = "/e2e/mailbox";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<LinkEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(LinkEmail email) {
        sent.add(email);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse out = (HttpServletResponse) response;
        if (!"GET".equals(http.getMethod())) {
            out.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        String to = http.getParameter("to");
        LinkEmail latest = sent.stream().filter(email -> email.recipient().equals(to)).reduce((a, b) -> b).orElse(null);
        if (latest == null) {
            out.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        out.setStatus(HttpServletResponse.SC_OK);
        out.setContentType("application/json");
        out.setHeader("Cache-Control", "no-store");
        out.getOutputStream().write(JSON.writeValueAsString(Map.of("type", latest.type().name(),
                "link", latest.link().toString())).getBytes(StandardCharsets.UTF_8));
    }
}

package sg.securedhello.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLogFormatterFactory;
import org.springframework.mock.env.MockEnvironment;

import sg.securedhello.logging.EcsLogFormatter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the application's ECS NDJSON output in tests: each line becomes a map keyed by dotted field name, whether
 * the line nests the field ({@code {"event":{"action":..}}}) or not. Also renders a captured event through the real
 * {@link EcsLogFormatter}, for unit tests that capture events rather than output.
 */
public final class EcsJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private EcsJson() {
    }

    /** Every JSON line of {@code output}, flattened; other lines are skipped. */
    public static List<Map<String, Object>> rows(String output) {
        return output.lines().filter(line -> line.startsWith("{")).map(EcsJson::flatten).toList();
    }

    /** The rows of {@code output} whose {@code event.action} is {@code action}. */
    public static List<Map<String, Object>> rowsWithAction(String output, String action) {
        return rows(output).stream().filter(row -> action.equals(row.get("event.action"))).toList();
    }

    /** One JSON line as dotted field name to value; arrays become lists of their elements' text. */
    public static Map<String, Object> flatten(String line) {
        Map<String, Object> fields = new LinkedHashMap<>();
        flatten("", JSON.readTree(line), fields);
        return fields;
    }

    /** {@code event} as the application writes it, through {@link EcsLogFormatter}. */
    public static String render(ILoggingEvent event) {
        ThrowableProxyConverter converter = new ThrowableProxyConverter();
        converter.setContext(new LoggerContext());
        converter.start();
        StructuredLogFormatter<ILoggingEvent> formatter = new StructuredLogFormatterFactory<>(ILoggingEvent.class,
                new MockEnvironment().withProperty("spring.application.name", "secured-hello-world"),
                parameters -> parameters.add(ThrowableProxyConverter.class, converter), formatters -> {
                })
                .get(EcsLogFormatter.class.getName());
        return formatter.format(event);
    }

    private static void flatten(String prefix, JsonNode node, Map<String, Object> fields) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> member : node.properties()) {
                flatten(prefix.isEmpty() ? member.getKey() : prefix + "." + member.getKey(), member.getValue(),
                        fields);
            }
        } else if (node.isArray()) {
            List<String> elements = new ArrayList<>();
            node.forEach(element -> elements.add(element.asString()));
            fields.put(prefix, elements);
        } else {
            fields.put(prefix, node.isNumber() ? node.numberValue() : node.asString());
        }
    }
}

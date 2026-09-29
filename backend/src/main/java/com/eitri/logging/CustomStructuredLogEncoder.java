package com.eitri.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Post-processes Boot ECS output so dynamic workaround keys can safely extend sealed ECS objects. */
public class CustomStructuredLogEncoder extends StructuredLogEncoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId LOG_ZONE = ZoneId.of("Asia/Singapore");

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] original = super.encode(event);
        try {
            byte[] transformed = postProcess(original, event);
            byte[] ndjson = new byte[transformed.length + 1];
            System.arraycopy(transformed, 0, ndjson, 0, transformed.length);
            ndjson[transformed.length] = '\n';
            return ndjson;
        } catch (RuntimeException exception) {
            return original;
        }
    }

    byte[] postProcess(byte[] original, ILoggingEvent event) {
        JsonNode parsed = MAPPER.readTree(original);
        if (!(parsed instanceof ObjectNode root)) {
            return original;
        }

        normalizeTimestamp(root);
        normalizeTraceContext(root);

        Map<String, ObjectNode> targets = new HashMap<>();
        Iterator<String> names = root.propertyNames().iterator();
        List<String> removals = new ArrayList<>();
        while (names.hasNext()) {
            String fieldName = names.next();
            if (fieldName.matches("(error|service|log|process|ecs)_.+")) {
                String[] parts = fieldName.split("_", 2);
                ObjectNode target = targets.computeIfAbsent(parts[0], ignored -> objectAt(root, parts[0]));
                target.set(parts[1], root.get(fieldName));
                removals.add(fieldName);
            }
        }
        removals.forEach(root::remove);

        if (event.getThrowableProxy() instanceof ThrowableProxy proxy) {
            ObjectNode error = targets.computeIfAbsent("error", ignored -> objectAt(root, "error"));
            if (!error.has("category")) {
                error.put("category", ErrorCategoryResolver.resolve(proxy.getThrowable()));
            }
        }

        targets.forEach(root::set);
        return MAPPER.writeValueAsBytes(root);
    }

    private static void normalizeTimestamp(ObjectNode root) {
        JsonNode timestamp = root.get("@timestamp");
        if (timestamp != null && timestamp.isString()) {
            OffsetDateTime instant = OffsetDateTime.parse(timestamp.asString());
            root.put("@timestamp", instant.atZoneSameInstant(LOG_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    private static void normalizeTraceContext(ObjectNode root) {
        JsonNode traceId = root.remove("traceId");
        JsonNode spanId = root.remove("spanId");
        if (traceId == null && spanId == null) {
            return;
        }
        if (traceId != null) {
            ObjectNode trace = objectAt(root, "trace");
            trace.set("id", traceId);
            root.set("trace", trace);
        }
        if (spanId != null) {
            ObjectNode span = objectAt(root, "span");
            span.set("id", spanId);
            root.set("span", span);
        }
    }

    private static ObjectNode objectAt(ObjectNode root, String name) {
        JsonNode existing = root.get(name);
        return existing instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
    }
}

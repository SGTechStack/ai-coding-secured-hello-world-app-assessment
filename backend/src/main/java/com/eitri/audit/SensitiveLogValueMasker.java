package com.eitri.audit;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** Masks accidentally-added structured values whose field names indicate credentials or session data. */
public final class SensitiveLogValueMasker implements StructuredLoggingJsonMembersCustomizer<Object> {

    public static final String MASK = "***MASKED***";
    private static final Pattern SENSITIVE_KEY = Pattern.compile("password|token|secret|session", Pattern.CASE_INSENSITIVE);

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor((path, value) -> shouldMask(path, value) ? MASK : value);
    }

    private static boolean shouldMask(JsonWriter.MemberPath path, Object value) {
        // Maps must remain traversable so session.hash can be preserved while sibling session fields are masked.
        if (path == null || path.name() == null || value instanceof Map<?, ?>) {
            return false;
        }

        String fullPath = path.toUnescapedString().toLowerCase(Locale.ROOT);
        String fieldName = path.name().toLowerCase(Locale.ROOT);
        if (fullPath.equals("session.hash") || fieldName.equals("session.hash")) {
            return false;
        }
        return SENSITIVE_KEY.matcher(fullPath).find() || SENSITIVE_KEY.matcher(fieldName).find();
    }
}

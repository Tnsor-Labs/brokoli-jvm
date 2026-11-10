package dev.brokoli.task.processor;

import java.util.List;
import java.util.Map;

final class JsonWriter {

    private static final int FIRST_PRINTABLE = 0x20;

    private JsonWriter() {
    }

    static String write(Object value) {
        StringBuilder json = new StringBuilder();
        append(json, value);
        return json.toString();
    }

    private static void append(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String text) {
            appendString(json, text);
        } else if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            json.append(value);
        } else if (value instanceof Double number) {
            appendDouble(json, number);
        } else if (value instanceof Map<?, ?> map) {
            appendObject(json, map);
        } else if (value instanceof List<?> list) {
            appendArray(json, list);
        } else {
            throw new IllegalArgumentException("no JSON form for " + value.getClass().getName());
        }
    }

    private static void appendDouble(StringBuilder json, double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("JSON has no form for " + number);
        }
        json.append(number);
    }

    private static void appendObject(StringBuilder json, Map<?, ?> map) {
        json.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendString(json, (String) entry.getKey());
            json.append(':');
            append(json, entry.getValue());
        }
        json.append('}');
    }

    private static void appendArray(StringBuilder json, List<?> list) {
        json.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            append(json, list.get(i));
        }
        json.append(']');
    }

    private static void appendString(StringBuilder json, String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < FIRST_PRINTABLE) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}

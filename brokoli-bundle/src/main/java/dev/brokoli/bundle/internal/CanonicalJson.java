package dev.brokoli.bundle.internal;

import dev.brokoli.bundle.BundleException;
import dev.brokoli.task.internal.JsonNumbers;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders JSON in the canonical form of core's {@code ir-canonicalization.md} section 2, and digests that
 * rendering as its section 3 says: keys sorted by Unicode code point, two-space indentation, literal UTF-8,
 * numbers as RFC 8785 writes them, and one trailing newline. Used by the bundle tooling and the pipeline builder;
 * not part of the API.
 */
public final class CanonicalJson {

    private static final String INDENT = "  ";
    private static final int FIRST_PRINTABLE = 0x20;

    private final StringBuilder json = new StringBuilder();

    private CanonicalJson() {
    }

    /**
     * Renders a JSON value canonically.
     *
     * @param value the value to render
     * @return the canonical rendering, ending in one newline
     * @throws BundleException if the value holds text that is not valid Unicode, or a node with no JSON form
     * @throws IllegalArgumentException if the value holds a number that is NaN or infinite
     */
    public static String render(JsonNode value) {
        CanonicalJson writer = new CanonicalJson();
        writer.value(value, 0);
        writer.json.append('\n');
        return writer.json.toString();
    }

    /**
     * The digest of a JSON value's canonical rendering: {@code sha256:} and the SHA-256 of its UTF-8 bytes.
     *
     * @param value the value to digest
     * @return the digest
     * @throws BundleException if the value holds text that is not valid Unicode, or a node with no JSON form
     * @throws IllegalArgumentException if the value holds a number that is NaN or infinite
     */
    public static String digest(JsonNode value) {
        return Digests.digest(Utf8.bytes(render(value)));
    }

    private void value(JsonNode value, int depth) {
        if (value.isObject()) {
            object(value, depth);
        } else if (value.isArray()) {
            array(value, depth);
        } else if (value.isString()) {
            string(value.stringValue());
        } else if (value.isIntegralNumber()) {
            json.append(value.bigIntegerValue());
        } else if (value.isFloatingPointNumber()) {
            json.append(JsonNumbers.format(value.doubleValue()));
        } else if (value.isBoolean()) {
            json.append(value.booleanValue());
        } else if (value.isNull()) {
            json.append("null");
        } else {
            throw new BundleException("no canonical JSON form for a " + value.getNodeType() + " value");
        }
    }

    private void object(JsonNode object, int depth) {
        List<Map.Entry<String, JsonNode>> properties = new ArrayList<>(object.properties());
        if (properties.isEmpty()) {
            json.append("{}");
            return;
        }
        properties.sort(Map.Entry.comparingByKey(Utf8.ORDER));
        json.append('{');
        for (int i = 0; i < properties.size(); i++) {
            json.append(i == 0 ? "\n" : ",\n");
            indent(depth + 1);
            string(properties.get(i).getKey());
            json.append(": ");
            value(properties.get(i).getValue(), depth + 1);
        }
        json.append('\n');
        indent(depth);
        json.append('}');
    }

    private void array(JsonNode array, int depth) {
        if (array.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append('[');
        int i = 0;
        for (JsonNode item : array.values()) {
            json.append(i++ == 0 ? "\n" : ",\n");
            indent(depth + 1);
            value(item, depth + 1);
        }
        json.append('\n');
        indent(depth);
        json.append(']');
    }

    private void indent(int depth) {
        json.append(INDENT.repeat(depth));
    }

    private void string(String text) {
        Utf8.requireValid(text);
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

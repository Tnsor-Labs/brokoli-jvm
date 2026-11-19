package dev.brokoli.pipeline;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

final class JsonValues {

    private static final String ALLOWED_VALUES = "a JSON value is null, a String, a Boolean, an Integer, a Long,"
            + " a BigInteger, a finite Double, a List, or a Map with String keys";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private JsonValues() {
    }

    static ArrayNode rules(String path, List<Map<String, Object>> rules) {
        ArrayNode parsed = JSON.arrayNode();
        for (int i = 0; i < rules.size(); i++) {
            parsed.add(of(path + "[" + i + "]", rules.get(i)));
        }
        return parsed;
    }

    static JsonNode of(String path, Object value) {
        if (value == null) {
            return JSON.nullNode();
        }
        if (value instanceof String text) {
            return JSON.stringNode(text);
        }
        if (value instanceof Boolean flag) {
            return JSON.booleanNode(flag);
        }
        if (value instanceof Integer || value instanceof Long) {
            return JSON.numberNode(((Number) value).longValue());
        }
        if (value instanceof BigInteger integer) {
            return JSON.numberNode(integer);
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return JSON.numberNode(number);
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode object = JSON.objectNode();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new PipelineException(path + " has the key " + entry.getKey() + " of type "
                            + className(entry.getKey()) + "; " + ALLOWED_VALUES);
                }
                object.set(key, of(path + "." + key, entry.getValue()));
            }
            return object;
        }
        if (value instanceof List<?> list) {
            ArrayNode array = JSON.arrayNode();
            for (int i = 0; i < list.size(); i++) {
                array.add(of(path + "[" + i + "]", list.get(i)));
            }
            return array;
        }
        throw new PipelineException(path + " is " + (value instanceof Double ? value : "a " + className(value))
                + "; " + ALLOWED_VALUES);
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}

package dev.brokoli.pipeline;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TransformRules {

    private static final String TYPE = "type";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String MAPPING = "mapping";
    private static final Set<String> RENAME_TYPES = Set.of("rename", "rename_columns");
    private static final String ALLOWED_VALUES = "a rule value is null, a String, a Boolean, an Integer, a Long,"
            + " a BigInteger, a finite Double, a List, or a Map with String keys";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private TransformRules() {
    }

    static ArrayNode parse(String node, List<Map<String, Object>> rules) {
        ArrayNode parsed = JSON.arrayNode();
        for (int i = 0; i < rules.size(); i++) {
            String path = "transform " + node + ": " + IrFields.RULES + "[" + i + "]";
            Map<String, Object> rule = new LinkedHashMap<>(rules.get(i));
            if (rule.get(TYPE) instanceof String type && RENAME_TYPES.contains(type)) {
                rename(path, rule);
            }
            parsed.add(json(path, rule));
        }
        return parsed;
    }

    private static void rename(String path, Map<String, Object> rule) {
        boolean from = rule.containsKey(FROM);
        boolean to = rule.containsKey(TO);
        if (from && to && !rule.containsKey(MAPPING)) {
            Map<Object, Object> mapping = new LinkedHashMap<>();
            mapping.put(rule.remove(FROM), rule.remove(TO));
            rule.put(MAPPING, mapping);
        } else if (from || to) {
            throw new PipelineException(path + " (" + rule.get(TYPE) + ") has the \"from\" and \"to\" shorthand"
                    + " without both keys, or with a \"mapping\" too; give both, or only a \"mapping\" of old to new"
                    + " column names");
        }
        if (!(rule.get(MAPPING) instanceof Map<?, ?> mapping) || mapping.isEmpty()) {
            throw new PipelineException(path + " (" + rule.get(TYPE) + ") needs a non-empty \"mapping\" of old to"
                    + " new column names, such as Map.of(\"type\", \"rename\", \"mapping\", Map.of(\"old_col\","
                    + " \"new_col\"))");
        }
    }

    private static JsonNode json(String path, Object value) {
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
                object.set(key, json(path + "." + key, entry.getValue()));
            }
            return object;
        }
        if (value instanceof List<?> list) {
            ArrayNode array = JSON.arrayNode();
            for (int i = 0; i < list.size(); i++) {
                array.add(json(path + "[" + i + "]", list.get(i)));
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

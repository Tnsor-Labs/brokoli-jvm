package dev.brokoli.pipeline;

import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
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

    private TransformRules() {
    }

    static ArrayNode parse(String node, List<Map<String, Object>> rules) {
        String path = IrFields.TRANSFORM + " " + node + ": " + IrFields.RULES;
        List<Map<String, Object>> shaped = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Map<String, Object> rule = new LinkedHashMap<>(rules.get(i));
            if (rule.get(TYPE) instanceof String type && RENAME_TYPES.contains(type)) {
                rename(path + "[" + i + "]", rule);
            }
            shaped.add(rule);
        }
        return JsonValues.rules(path, shaped);
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
}

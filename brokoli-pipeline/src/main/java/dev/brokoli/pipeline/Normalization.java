package dev.brokoli.pipeline;

import dev.brokoli.bundle.internal.CanonicalJson;
import dev.brokoli.bundle.internal.Utf8;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Normalization {

    private static final List<String> SERVER_FIELDS = List.of(IrFields.ID, "source", "workspace_id", "org_id",
            "created_at", "updated_at");
    private static final List<String> LIST_DEFAULTS = List.of(IrFields.NODES, IrFields.EDGES, IrFields.TAGS,
            IrFields.DEPENDS_ON, IrFields.DEPENDENCY_RULES);
    private static final List<String> MAP_DEFAULTS = List.of(IrFields.PARAMS, IrFields.HOOKS);
    private static final List<String> DEFAULT_TIMEZONES = List.of("", "UTC");
    private static final List<String> SORTED_LISTS = List.of(IrFields.TAGS, IrFields.DEPENDS_ON);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Comparator<JsonNode> BY_RENDERING = Comparator.comparing(CanonicalJson::render, Utf8.ORDER);
    private static final Comparator<JsonNode> BY_ID = Comparator.comparing(node -> node.get(IrFields.ID),
            BY_RENDERING);

    private Normalization() {
    }

    static ObjectNode normalize(ObjectNode ir) {
        ObjectNode normalized = ir.deepCopy();
        SERVER_FIELDS.forEach(normalized::remove);
        for (String field : LIST_DEFAULTS) {
            if (absentOrNull(normalized.get(field))) {
                normalized.putArray(field);
            }
        }
        for (String field : MAP_DEFAULTS) {
            if (absentOrNull(normalized.get(field))) {
                normalized.putObject(field);
            }
        }
        if (isText(normalized.get(IrFields.WEBHOOK_URL), List.of(""))) {
            normalized.remove(IrFields.WEBHOOK_URL);
        }
        JsonNode timezone = normalized.get(IrFields.SCHEDULE_TIMEZONE);
        if (timezone != null && (timezone.isNull() || isText(timezone, DEFAULT_TIMEZONES))) {
            normalized.remove(IrFields.SCHEDULE_TIMEZONE);
        }
        if (normalized.has(IrFields.WEBHOOK_TOKEN)) {
            normalized.put(IrFields.WEBHOOK_TOKEN, "");
        }
        normalized.set(IrFields.NODES, sorted(normalized.get(IrFields.NODES).values().stream()
                .map(Normalization::node).toList(), BY_ID));
        for (String field : SORTED_LISTS) {
            normalized.set(field, sorted(normalized.get(field).values().stream().toList(), BY_RENDERING));
        }
        return normalized;
    }

    private static JsonNode node(JsonNode node) {
        ObjectNode normalized = (ObjectNode) node.deepCopy();
        normalized.remove(IrFields.POSITION);
        JsonNode capabilities = normalized.get(IrFields.CAPABILITIES);
        if (absentOrNull(capabilities) || capabilities.isEmpty()) {
            ArrayNode defaults = JSON.arrayNode();
            Capabilities.of(normalized.get(IrFields.TYPE).stringValue()).forEach(defaults::add);
            capabilities = defaults;
        }
        normalized.set(IrFields.CAPABILITIES, sorted(capabilities.values().stream().toList(), BY_RENDERING));
        return normalized;
    }

    private static ArrayNode sorted(List<JsonNode> values, Comparator<JsonNode> order) {
        List<JsonNode> copy = new ArrayList<>(values);
        copy.sort(order);
        ArrayNode array = JSON.arrayNode();
        copy.forEach(array::add);
        return array;
    }

    private static boolean absentOrNull(JsonNode value) {
        return value == null || value.isNull();
    }

    private static boolean isText(JsonNode value, List<String> texts) {
        return value != null && value.isString() && texts.contains(value.stringValue());
    }
}

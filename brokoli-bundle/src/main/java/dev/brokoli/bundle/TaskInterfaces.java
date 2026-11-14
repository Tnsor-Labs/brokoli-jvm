package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.CanonicalJson;
import dev.brokoli.bundle.internal.Utf8;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class TaskInterfaces {

    private static final String CONTRACT = "contract";
    private static final String CONTRACT_V1 = "brokoli.task-interface/v1";
    private static final String INPUTS = "inputs";
    private static final String OUTPUTS = "outputs";
    private static final String INPUT = "input";
    private static final String RESULT = "result";
    private static final String VALUE = "value";
    private static final String KIND = "kind";
    private static final String DATASET = "dataset";
    private static final String ROW = "row";
    private static final String UNKNOWN = "unknown";
    private static final String RECORD = "record";
    private static final String FIELDS = "fields";
    private static final String NAME = "name";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final Comparator<JsonNode> BY_NAME =
            Comparator.comparing(field -> field.path(NAME).asString(""), Utf8.ORDER);

    private TaskInterfaces() {
    }

    static JsonNode withPorts(JsonNode nodeInterface) {
        if (nodeInterface != null && !nodeInterface.isNull()) {
            return nodeInterface;
        }
        ObjectNode filled = NODES.objectNode();
        filled.put(CONTRACT, CONTRACT_V1);
        filled.set(INPUTS, NODES.objectNode().set(INPUT, unknownDataset()));
        filled.set(OUTPUTS, NODES.objectNode().set(RESULT, unknownDataset()));
        return filled;
    }

    static String interfaceDigest(JsonNode nodeInterface) {
        return CanonicalJson.digest(forHashing(withPorts(nodeInterface)));
    }

    static JsonNode forHashing(JsonNode value) {
        if (value.isObject()) {
            ObjectNode copy = NODES.objectNode();
            for (Map.Entry<String, JsonNode> property : value.properties()) {
                copy.set(property.getKey(), forHashing(property.getValue()));
            }
            if (RECORD.equals(copy.path(KIND).asString(null)) && copy.path(FIELDS).isArray()) {
                copy.set(FIELDS, sortedByName(copy.path(FIELDS)));
            }
            return copy;
        }
        if (value.isArray()) {
            ArrayNode copy = NODES.arrayNode();
            for (JsonNode item : value.values()) {
                copy.add(forHashing(item));
            }
            return copy;
        }
        return value;
    }

    private static ArrayNode sortedByName(JsonNode fields) {
        List<JsonNode> sorted = new ArrayList<>(fields.values());
        sorted.sort(BY_NAME);
        ArrayNode copy = NODES.arrayNode();
        sorted.forEach(copy::add);
        return copy;
    }

    private static ObjectNode unknownDataset() {
        ObjectNode row = NODES.objectNode();
        row.put(KIND, UNKNOWN);
        ObjectNode dataset = NODES.objectNode();
        dataset.put(KIND, DATASET);
        dataset.set(ROW, row);
        return NODES.objectNode().set(VALUE, dataset);
    }
}

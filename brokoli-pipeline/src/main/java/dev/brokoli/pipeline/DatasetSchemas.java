package dev.brokoli.pipeline;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class DatasetSchemas {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String CONTRACT = "brokoli.dataset-schema/v1";
    private static final String UNKNOWN_KIND = "unknown";
    private static final String RIGHT_PREFIX = "right_";
    private static final String ALIAS_SEPARATOR = "_";

    private DatasetSchemas() {
    }

    static ObjectNode json(DatasetSchema schema) {
        ObjectNode written = JSON.objectNode();
        written.put(IrFields.CONTRACT, CONTRACT);
        ArrayNode columns = written.putArray(IrFields.COLUMNS);
        for (Column column : schema.columns()) {
            ObjectNode writtenColumn = columns.addObject();
            writtenColumn.put(IrFields.NAME, column.name());
            writtenColumn.set(IrFields.TYPE, column.type().toJson());
        }
        written.put(IrFields.ADDITIONAL_COLUMNS, schema.additionalColumns().irValue());
        return written;
    }

    static DatasetSchema join(String joinName, DatasetSchema left, DatasetSchema right, Join options) {
        String leftKey = options.leftKey();
        String rightKey = options.rightKey();
        Map<String, Column> leftColumns = byName(left);
        Map<String, Column> rightColumns = byName(right);
        Column leftKeyColumn = requireKey(joinName, "left", leftKey, leftColumns);
        Column rightKeyColumn = requireKey(joinName, "right", rightKey, rightColumns);
        String leftKind = leftKeyColumn.type().kind();
        String rightKind = rightKeyColumn.type().kind();
        if (!leftKind.equals(rightKind) && !leftKind.equals(UNKNOWN_KIND) && !rightKind.equals(UNKNOWN_KIND)) {
            throw new PipelineException(IrFields.JOIN + " " + joinName + ": the keys " + quoted(leftKey) + " and "
                    + quoted(rightKey) + " have incompatible declared types " + leftKind + " and " + rightKind);
        }
        boolean sameKey = leftKey.equals(rightKey);
        List<String> collisions = right.columns().stream()
                .map(Column::name)
                .filter(name -> leftColumns.containsKey(name) && !(sameKey && name.equals(rightKey)))
                .toList();
        if (options.collisionPolicy() == CollisionPolicy.ERROR && !collisions.isEmpty()) {
            throw new PipelineException(IrFields.JOIN + " " + joinName + ": the error collision policy refuses the"
                    + " right columns " + collisions.stream().map(DatasetSchemas::quoted)
                    .collect(Collectors.joining(", ")) + ", which the left input has too");
        }
        List<Column> output = new ArrayList<>(left.columns());
        Set<String> used = new HashSet<>(leftColumns.keySet());
        for (Column column : right.columns()) {
            if (sameKey && column.name().equals(rightKey)) {
                continue;
            }
            String outputName = outputName(column.name(), options, !collisions.isEmpty(), used);
            if (!used.add(outputName)) {
                throw new PipelineException(IrFields.JOIN + " " + joinName + ": the right column "
                        + quoted(column.name()) + " is written as " + quoted(outputName)
                        + ", which the output already has");
            }
            output.add(new Column(outputName, column.type()));
        }
        boolean closed = left.additionalColumns() == AdditionalColumns.CLOSED
                && right.additionalColumns() == AdditionalColumns.CLOSED;
        return new DatasetSchema(output, closed ? AdditionalColumns.CLOSED : AdditionalColumns.UNKNOWN);
    }

    private static Map<String, Column> byName(DatasetSchema schema) {
        Map<String, Column> columns = new LinkedHashMap<>();
        schema.columns().forEach(column -> columns.put(column.name(), column));
        return columns;
    }

    private static Column requireKey(String joinName, String side, String key, Map<String, Column> columns) {
        Column column = columns.get(key);
        if (column == null) {
            throw new PipelineException(IrFields.JOIN + " " + joinName + ": the " + side + " key " + quoted(key)
                    + " is not a column of the schema the " + side + " input declares");
        }
        return column;
    }

    private static String outputName(String name, Join options, boolean collides, Set<String> used) {
        return switch (options.collisionPolicy()) {
            case ALIAS -> options.rightAlias().orElseThrow() + ALIAS_SEPARATOR + name;
            case PREFIX -> collides ? prefixed(name, used) : name;
            case ERROR -> name;
        };
    }

    private static String prefixed(String name, Set<String> used) {
        String prefixed = RIGHT_PREFIX + name;
        while (used.contains(prefixed)) {
            prefixed = RIGHT_PREFIX + prefixed;
        }
        return prefixed;
    }

    private static String quoted(String name) {
        return "\"" + name + "\"";
    }
}

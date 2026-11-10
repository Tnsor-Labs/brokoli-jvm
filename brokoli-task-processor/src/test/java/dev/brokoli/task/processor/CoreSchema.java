package dev.brokoli.task.processor;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;

import java.util.List;

final class CoreSchema {

    private static final String SCHEMA_BASE = "https://github.com/Tnsor-Labs/brokoli/docs/schema";
    private static final String TASK_INTERFACE = SCHEMA_BASE + "/task-interface-v1.json";
    private static final String PIPELINE_PARAMETERS = TASK_INTERFACE + "#/$defs/pipeline_parameters";
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaIdResolvers(resolvers -> resolvers.mapPrefix(SCHEMA_BASE, "classpath:schema")));

    private CoreSchema() {
    }

    static List<String> interfaceErrors(JsonNode taskInterface) {
        return errors(TASK_INTERFACE, taskInterface);
    }

    static List<String> parameterErrors(JsonNode parameters) {
        return errors(PIPELINE_PARAMETERS, parameters);
    }

    private static List<String> errors(String location, JsonNode value) {
        Schema schema = REGISTRY.getSchema(SchemaLocation.of(location));
        return schema.validate(TestCompiler.JSON.writeValueAsString(value), InputFormat.JSON).stream()
                .map(String::valueOf)
                .toList();
    }
}

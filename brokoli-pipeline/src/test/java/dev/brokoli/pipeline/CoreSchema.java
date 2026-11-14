package dev.brokoli.pipeline;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.util.List;

final class CoreSchema {

    private static final String SCHEMA_BASE = "https://github.com/Tnsor-Labs/brokoli/docs/schema";
    private static final String PIPELINE_IR = SCHEMA_BASE + "/pipeline-ir-2.2.json";
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaIdResolvers(resolvers -> resolvers.mapPrefix(SCHEMA_BASE, "classpath:schema")));

    private CoreSchema() {
    }

    static List<String> pipelineErrors(String ir) {
        Schema schema = REGISTRY.getSchema(SchemaLocation.of(PIPELINE_IR));
        return schema.validate(ir, InputFormat.JSON).stream()
                .map(String::valueOf)
                .toList();
    }
}

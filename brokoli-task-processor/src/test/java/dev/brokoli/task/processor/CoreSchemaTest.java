package dev.brokoli.task.processor;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class CoreSchemaTest {

    @Test
    void acceptsAMinimalInterfaceAndRefusesAWrongContract() {
        assertThat(CoreSchema.interfaceErrors(json("""
                {"contract": "brokoli.task-interface/v1", "inputs": {}, "outputs": {}}"""))).isEmpty();
        assertThat(CoreSchema.interfaceErrors(json("""
                {"contract": "brokoli.task-interface/v2", "inputs": {}, "outputs": {}}"""))).isNotEmpty();
    }

    @Test
    void refusesANullableThatIsNotABoolean() {
        assertThat(CoreSchema.interfaceErrors(json("""
                {"contract": "brokoli.task-interface/v1", "inputs": {}, "outputs": {
                    "x": {"value": {"kind": "scalar", "type": {"kind": "string", "nullable": "yes"}}}
                }}"""))).isNotEmpty();
    }

    @Test
    void acceptsAnOptionalParameterWithADefaultAndRefusesARequiredOne() {
        assertThat(CoreSchema.parameterErrors(json("""
                {"threshold": {"type": {"kind": "float64"}, "required": false, "default": 0.5}}"""))).isEmpty();
        assertThat(CoreSchema.parameterErrors(json("""
                {"threshold": {"type": {"kind": "float64"}, "required": true, "default": 0.5}}"""))).isNotEmpty();
    }

    private static JsonNode json(String text) {
        return TestCompiler.JSON.readTree(text);
    }
}

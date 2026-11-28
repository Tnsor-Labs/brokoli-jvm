package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataTypeTest {

    @Test
    void writesEachKindAsTheDescriptorCoresSchemaAccepts() {
        Map<DataType, String> written = new LinkedHashMap<>();
        written.put(DataType.bool(), """
                {"kind":"boolean"}""");
        written.put(DataType.int64(), """
                {"kind":"int64"}""");
        written.put(DataType.float64(), """
                {"kind":"float64"}""");
        written.put(DataType.decimal(), """
                {"kind":"decimal"}""");
        written.put(DataType.decimal(18, 2), """
                {"kind":"decimal","precision":18,"scale":2}""");
        written.put(DataType.string(), """
                {"kind":"string"}""");
        written.put(DataType.bytes(), """
                {"kind":"bytes"}""");
        written.put(DataType.date(), """
                {"kind":"date"}""");
        written.put(DataType.timestamp(), """
                {"kind":"timestamp"}""");
        written.put(DataType.duration(), """
                {"kind":"duration"}""");
        written.put(DataType.json(), """
                {"kind":"json"}""");
        written.put(DataType.unknown(), """
                {"kind":"unknown"}""");
        written.put(DataType.enumOf("new", "paid"), """
                {"kind":"enum","values":["new","paid"]}""");
        written.put(DataType.array(DataType.string().nullable()), """
                {"kind":"array","items":{"kind":"string","nullable":true}}""");
        written.put(DataType.map(DataType.float64()), """
                {"kind":"map","keys":"string","values":{"kind":"float64"}}""");
        written.put(DataType.record(RecordField.of("city", DataType.string()),
                RecordField.of("zip", DataType.string()).optional()), """
                {"kind":"record","fields":[{"name":"city","type":{"kind":"string"},"required":true},\
                {"name":"zip","type":{"kind":"string"},"required":false}],"additional_fields":false}""");

        written.forEach((type, descriptor) -> {
            assertThat(type.toString()).isEqualTo(descriptor);
            assertThat(CoreSchema.datasetSchemaErrors(DatasetSchemas.json(DatasetSchema.of(
                    Column.of("value", type), Column.of("nullable_value", type.nullable()))).toString()))
                    .as(descriptor)
                    .isEmpty();
        });
    }

    @Test
    void writesANullableCopyAndLeavesTheTypeItCameFromAsItWas() {
        DataType text = DataType.string();

        DataType nullable = text.nullable();

        assertThat(nullable.toString()).isEqualTo("""
                {"kind":"string","nullable":true}""");
        assertThat(text.toString()).isEqualTo("""
                {"kind":"string"}""");
        assertThat(nullable).isEqualTo(DataType.string().nullable()).isNotEqualTo(text);
        assertThat(nullable).hasSameHashCodeAs(DataType.string().nullable());
    }

    @Test
    void refusesADecimalWhosePrecisionOrScaleCoreRefuses() {
        assertThat(DataType.decimal(1, 0)).hasToString("""
                {"kind":"decimal","precision":1,"scale":0}""");
        assertThat(DataType.decimal(4, 4)).hasToString("""
                {"kind":"decimal","precision":4,"scale":4}""");
        assertThatThrownBy(() -> DataType.decimal(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the precision of a decimal is at least 1, but it is 0");
        assertThatThrownBy(() -> DataType.decimal(10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the scale of a decimal is at least 0, but it is -1");
        assertThatThrownBy(() -> DataType.decimal(4, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the scale 5 of a decimal is over its precision 4");
    }

    @Test
    void refusesAnEnumWithNoValueOrANullValue() {
        assertThatThrownBy(DataType::enumOf)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an enum needs at least one value");
        assertThatThrownBy(() -> DataType.enumOf("new", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void refusesARecordWithTwoFieldsOfTheSameNameOrAFieldWithNoName() {
        assertThatThrownBy(() -> DataType.record(RecordField.of("city", DataType.string()),
                RecordField.of("city", DataType.int64())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the record has two fields named city");
        assertThatThrownBy(() -> RecordField.of("", DataType.string()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a record field needs a name");
    }

    @Test
    void refusesANullNestedType() {
        assertThatThrownBy(() -> DataType.array(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items");
        assertThatThrownBy(() -> DataType.map(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("values");
        assertThatThrownBy(() -> RecordField.of("city", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type");
    }
}

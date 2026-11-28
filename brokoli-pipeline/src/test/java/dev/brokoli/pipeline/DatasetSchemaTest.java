package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetSchemaTest {

    @Test
    void writesTheColumnsInTheirOrderAsCoresDatasetSchema() {
        DatasetSchema schema = DatasetSchema.of(Column.of("id", DataType.int64()),
                Column.of("name", DataType.string().nullable()));

        assertThat(DatasetSchemas.json(schema).toString()).isEqualTo("""
                {"contract":"brokoli.dataset-schema/v1","columns":[{"name":"id","type":{"kind":"int64"}},\
                {"name":"name","type":{"kind":"string","nullable":true}}],"additional_columns":"unknown"}""");
        assertThat(CoreSchema.datasetSchemaErrors(DatasetSchemas.json(schema).toString())).isEmpty();
    }

    @Test
    void writesWhetherTheDatasetMayHaveOtherColumnsUnknownUnlessSet() {
        DatasetSchema schema = DatasetSchema.of(Column.of("id", DataType.int64()));

        assertThat(DatasetSchemas.json(schema).path("additional_columns").stringValue()).isEqualTo("unknown");
        assertThat(DatasetSchemas.json(schema.closed()).path("additional_columns").stringValue())
                .isEqualTo("closed");
        assertThat(DatasetSchemas.json(schema.open()).path("additional_columns").stringValue()).isEqualTo("open");
        assertThat(schema.closed().columns()).isEqualTo(schema.columns());
    }

    @Test
    void writesASchemaWithNoColumnAsThePythonSdkDoes() {
        assertThat(DatasetSchemas.json(DatasetSchema.of()).toString()).isEqualTo("""
                {"contract":"brokoli.dataset-schema/v1","columns":[],"additional_columns":"unknown"}""");
    }

    @Test
    void keepsItsOwnCopyOfTheColumns() {
        List<Column> columns = new ArrayList<>(List.of(Column.of("id", DataType.int64())));
        DatasetSchema schema = new DatasetSchema(columns, AdditionalColumns.CLOSED);

        columns.add(Column.of("name", DataType.string()));

        assertThat(schema.columns()).containsExactly(Column.of("id", DataType.int64()));
    }

    @Test
    void refusesTwoColumnsOfTheSameNameOrAColumnWithNoName() {
        assertThatThrownBy(() -> DatasetSchema.of(Column.of("id", DataType.int64()),
                Column.of("id", DataType.string())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the dataset schema has two columns named id");
        assertThatThrownBy(() -> Column.of("", DataType.int64()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a dataset column needs a name");
        assertThatThrownBy(() -> DatasetSchema.of(Column.of("id", DataType.int64()), null))
                .isInstanceOf(NullPointerException.class);
    }
}

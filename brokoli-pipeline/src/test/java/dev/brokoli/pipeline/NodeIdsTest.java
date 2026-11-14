package dev.brokoli.pipeline;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NodeIdsTest {

    private static final String SCRIPT_A = "𝒜";

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "Read A|read_a",
            "Read|read",
            "'  '|__",
            "!!!|node",
            "''|node",
            "İstanbul Daily|istanbul_daily",
            "Ⅻ ½ ² x|ⅻ_½_²_x",
            "Straße Größe|straße_größe",
            "ΟΔΟΣ|οδος",
            "a-b.c/d|abcd",
            "Twenty one characters|twenty_one_character"})
    void makesTheIdBaseOfANameAsThePythonSdkDoes(String name, String base) {
        assertThat(NodeIds.base(name)).isEqualTo(base);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "22|20",
            "3|3"})
    void cutsTheIdBaseAtTwentyCodePointsNotUtf16Units(int letters, int kept) {
        assertThat(NodeIds.base(SCRIPT_A.repeat(letters))).isEqualTo(SCRIPT_A.repeat(kept));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "Sales ETL|sales-etl",
            "'  Sales -- ETL!! '|sales-etl",
            "Ünïcode Ünit|ncode-nit",
            "a__b|ab",
            "---|''",
            "İx|ix"})
    void makesThePipelineIdOfANameAsThePythonSdkDoes(String name, String pipelineId) {
        assertThat(NodeIds.pipelineId(name)).isEqualTo(pipelineId);
    }
}

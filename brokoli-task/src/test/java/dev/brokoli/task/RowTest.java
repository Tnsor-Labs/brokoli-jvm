package dev.brokoli.task;

import dev.brokoli.task.exception.RowValueException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowTest {

    @Test
    void getDoubleReadsAWholeNumberThatJsonDecodedAsALong() {
        Row row = Row.of(Map.of("amount", 5L, "rate", 0.25));

        assertThat(row.getDouble("amount")).isEqualTo(5.0);
        assertThat(row.getDouble("rate")).isEqualTo(0.25);
    }

    @Test
    void getDoubleRefusesTextNamingTheColumnAndTheTypeFound() {
        Row row = Row.of(Map.of("amount", "5"));

        assertThatThrownBy(() -> row.getDouble("amount"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'amount' is of type String, expected a number");
    }

    @Test
    void getLongReadsAWholeNumberAndRefusesOneWithAFraction() {
        Row row = Row.of(Map.of("count", 3L, "ratio", 2.5));

        assertThat(row.getLong("count")).isEqualTo(3L);
        assertThatThrownBy(() -> row.getLong("ratio"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'ratio' is of type Double, expected a whole number");
    }

    @Test
    void getStringReadsTextAndRefusesANumber() {
        Row row = Row.of(Map.of("region", "eu-west", "count", 3L));

        assertThat(row.getString("region")).isEqualTo("eu-west");
        assertThatThrownBy(() -> row.getString("count"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'count' is of type Long, expected a string");
    }

    @Test
    void getBooleanReadsABooleanAndRefusesText() {
        Row row = Row.of(Map.of("active", true, "flag", "true"));

        assertThat(row.getBoolean("active")).isTrue();
        assertThatThrownBy(() -> row.getBoolean("flag"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'flag' is of type String, expected a boolean");
    }

    @Test
    void typedGettersTellAMissingColumnFromANullValue() {
        Row row = nullable("amount", null);

        assertThatThrownBy(() -> row.getDouble("amount"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'amount' is null, expected a number");
        assertThatThrownBy(() -> row.getDouble("price"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'price' is missing, expected a number")
                .satisfies(thrown -> {
                    RowValueException refused = (RowValueException) thrown;
                    assertThat(refused.getColumn()).isEqualTo("price");
                    assertThat(refused.getExpected()).isEqualTo("a number");
                    assertThat(refused.getFound()).isEqualTo("missing");
                });
    }

    @Test
    void isNullIsTrueForAMissingOrNullColumnAndFalseForAValue() {
        Row row = nullable("amount", null).with("count", 0L);

        assertThat(row.isNull("amount")).isTrue();
        assertThat(row.isNull("price")).isTrue();
        assertThat(row.isNull("count")).isFalse();
        assertThat(row.has("amount")).isTrue();
        assertThat(row.has("price")).isFalse();
    }

    @Test
    void ofKeepsColumnOrderAndNullValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("zeta", 1L);
        values.put("alpha", null);
        values.put("mid", "x");

        Row row = Row.of(values);

        assertThat(row.columns()).containsExactly("zeta", "alpha", "mid");
        assertThat(row.toMap()).containsEntry("alpha", null);
    }

    @Test
    void ofRefusesANullColumnName() {
        Map<String, Object> values = new HashMap<>();
        values.put(null, 1L);

        assertThatThrownBy(() -> Row.of(values))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("column");
    }

    @Test
    void laterChangesToTheSourceMapDoNotReachTheRow() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amount", 1L);
        Row row = Row.of(values);

        values.put("amount", 2L);
        values.put("extra", "x");

        assertThat(row.getLong("amount")).isEqualTo(1L);
        assertThat(row.has("extra")).isFalse();
    }

    @Test
    void toMapCannotBeModified() {
        Row row = Row.of(Map.of("amount", 1L));

        assertThatThrownBy(() -> row.toMap().put("amount", 2L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withReturnsAChangedCopyAndLeavesTheOriginalUnchanged() {
        Row original = Row.of(Map.of("amount", 1L));

        Row changed = original.with("amount", 2L);

        assertThat(changed.getLong("amount")).isEqualTo(2L);
        assertThat(original.getLong("amount")).isEqualTo(1L);
    }

    @Test
    void withKeepsAnExistingColumnInPlaceAndAddsANewOneLast() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("a", 1L);
        values.put("b", 2L);

        Row row = Row.of(values).with("a", 10L).with("c", 3L);

        assertThat(row.columns()).containsExactly("a", "b", "c");
        assertThat(row.getLong("a")).isEqualTo(10L);
    }

    @Test
    void rowsWithTheSameColumnsAreEqualInAnyOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("a", 1L);
        first.put("b", "x");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("b", "x");
        second.put("a", 1L);

        assertThat(Row.of(first)).isEqualTo(Row.of(second)).hasSameHashCodeAs(Row.of(second));
        assertThat(Row.of(first)).isNotEqualTo(Row.of(first).with("a", 2L));
    }

    @Test
    void columnsCannotBeModified() {
        Row row = Row.of(Map.of("amount", 1L));

        List<String> columns = row.columns();

        assertThatThrownBy(() -> columns.add("extra")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Row nullable(String column, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, value);
        return Row.of(values);
    }
}

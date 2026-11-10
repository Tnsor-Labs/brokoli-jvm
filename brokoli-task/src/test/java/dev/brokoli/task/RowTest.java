package dev.brokoli.task;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    void getDecimalReadsATaggedDecimalExactlyAndAPlainNumberThroughItsDouble() {
        Map<String, Object> tagged = new LinkedHashMap<>();
        tagged.put("$bptd", "decimal");
        tagged.put("value", "12.50");
        Row row = Row.of(Map.of("price", tagged, "count", 9_007_199_254_740_993L, "rate", 0.1));

        assertThat(row.getDecimal("price")).isEqualTo(new BigDecimal("12.50"));
        assertThat(row.getDecimal("count")).isEqualTo(new BigDecimal("9007199254740993"));
        assertThat(row.getDecimal("rate")).isEqualTo(new BigDecimal("0.1"));
    }

    @Test
    void getDecimalRefusesTextAndAMapThatIsNotATaggedDecimal() {
        Row row = Row.of(Map.of("price", "12.50", "attrs", Map.of("size", 3L)));

        assertThatThrownBy(() -> row.getDecimal("price"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'price' is of type String, expected a decimal: a number or a tagged decimal");
        assertThatThrownBy(() -> row.getDecimal("attrs"))
                .hasMessage("column 'attrs' is a map that is not a tagged decimal, expected a decimal: a number or "
                        + "a tagged decimal");
        assertThatThrownBy(() -> row.getDecimal("missing"))
                .hasMessage("column 'missing' is missing, expected a decimal: a number or a tagged decimal");
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
    void getLongReadsAnInteger() {
        Row row = Row.of(Map.of("count", 7));

        assertThat(row.getLong("count")).isEqualTo(7L);
    }

    @Test
    void getDoubleReadsAnIntegerAndAFloat() {
        Row row = Row.of(Map.of("count", 7, "ratio", 0.5f));

        assertThat(row.getDouble("count")).isEqualTo(7.0);
        assertThat(row.getDouble("ratio")).isEqualTo(0.5);
    }

    @Test
    void getDoubleReadsTheLargestLongsADoubleHoldsExactly() {
        Row row = Row.of(Map.of("limit", 1L << 53, "lowest", Long.MIN_VALUE, "power", 1L << 62));

        assertThat(row.getDouble("limit")).isEqualTo(0x1p53);
        assertThat(row.getDouble("lowest")).isEqualTo(-0x1p63);
        assertThat(row.getDouble("power")).isEqualTo(0x1p62);
    }

    @Test
    void getDoubleRefusesALongThatADoubleWouldRound() {
        Row row = Row.of(Map.of("id", (1L << 53) + 1, "highest", Long.MAX_VALUE));

        assertThatThrownBy(() -> row.getDouble("id"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'id' is a Long that a double cannot hold exactly, expected a number");
        assertThatThrownBy(() -> row.getDouble("highest"))
                .isInstanceOf(RowValueException.class)
                .hasMessage("column 'highest' is a Long that a double cannot hold exactly, expected a number");
    }

    @Test
    void ofAcceptsEveryJsonValueIncludingNestedListsAndMaps() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("text", "x");
        values.put("flag", true);
        values.put("whole", 1L);
        values.put("small", 1);
        values.put("fraction", 1.5);
        values.put("single", 1.5f);
        values.put("missing", null);
        values.put("tags", List.of("a", "b"));
        values.put("attrs", Map.of("size", 3L, "labels", List.of(Map.of("k", "v"))));

        Row row = Row.of(values);

        assertThat(row.columns()).hasSize(9);
    }

    @Test
    void ofRefusesValuesTheHarnessCannotWriteNamingTheColumn() {
        assertRefused("price", new BigDecimal("1.50"), "column 'price' is of type BigDecimal");
        assertRefused("day", LocalDate.of(2026, 11, 10), "column 'day' is of type LocalDate");
        assertRefused("n", (short) 1, "column 'n' is of type Short");
        assertRefused("b", (byte) 1, "column 'b' is of type Byte");
        assertRefused("ratio", Double.NaN, "column 'ratio' is a number that is not finite");
        assertRefused("scale", Float.POSITIVE_INFINITY, "column 'scale' is a number that is not finite");
        assertRefused("parent", Row.of(Map.of("id", 1L)), "column 'parent' is of type Row");
        assertRefused("codes", new String[] {"a"}, "column 'codes' is of type String[]");
    }

    @Test
    void ofRefusesABadValueNestedInAMapOrAListNamingItsPath() {
        Map<String, Object> attrs = Map.of("size", new BigDecimal("3"));
        List<Object> tags = List.of("a", "b", LocalDate.of(2026, 11, 10));

        assertRefused("attrs", attrs, "column 'attrs.size' is of type BigDecimal");
        assertRefused("tags", tags, "column 'tags[2]' is of type LocalDate");
        assertRefused("deep", List.of(Map.of("inner", List.of(Double.NaN))), "column 'deep[0].inner[0]' is a number that is not finite");
    }

    @Test
    void ofRefusesANestedMapWhoseKeysAreNotText() {
        Map<Object, Object> nullKey = new HashMap<>();
        nullKey.put(null, 1L);

        assertRefused("attrs", Map.of(1, "x"), "column 'attrs' is a map with a key of type Integer");
        assertRefused("attrs", nullKey, "column 'attrs' is a map with a null key");
    }

    @Test
    void withRefusesAValueTheHarnessCannotWriteAndLeavesTheRowUnchanged() {
        Row row = Row.of(Map.of("price", "1.50"));

        assertThatThrownBy(() -> row.with("price", new BigDecimal("1.50")))
                .isInstanceOf(RowValueException.class)
                .hasMessageStartingWith("column 'price' is of type BigDecimal, expected a JSON value");
        assertThat(row.getString("price")).isEqualTo("1.50");
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

    @Test
    void toStringShowsTheColumnsAndTheirValues() {
        Row row = Row.of(Map.of("amount", 5L));

        assertThat(row).hasToString("Row{amount=5}");
    }

    private static void assertRefused(String column, Object value, String messageStart) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, value);

        assertThatThrownBy(() -> Row.of(values))
                .isInstanceOf(RowValueException.class)
                .hasMessageStartingWith(messageStart + ", expected a JSON value");
    }

    private static Row nullable(String column, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, value);
        return Row.of(values);
    }
}

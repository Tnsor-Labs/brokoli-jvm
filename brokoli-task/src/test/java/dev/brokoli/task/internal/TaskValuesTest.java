package dev.brokoli.task.internal;

import dev.brokoli.task.Row;
import dev.brokoli.task.Rows;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskValuesTest {

    private enum Tier { gold, silver }

    @Test
    void readsEachScalarInTheFormCoreSends() {
        assertThat(TaskValues.readLong(42L)).isEqualTo(42L);
        assertThat(TaskValues.readLong(2.0)).isEqualTo(2L);
        assertThat(TaskValues.readLong(9007199254740991.0)).isEqualTo(9_007_199_254_740_991L);
        assertThat(TaskValues.readLong(-9007199254740991.0)).isEqualTo(-9_007_199_254_740_991L);
        assertThat(TaskValues.readLong(tagged("int64", "9223372036854775807"))).isEqualTo(Long.MAX_VALUE);
        assertThat(TaskValues.readInt(-2147483648L)).isEqualTo(Integer.MIN_VALUE);
        assertThat(TaskValues.readDouble(0.5)).isEqualTo(0.5);
        assertThat(TaskValues.readDouble(5L)).isEqualTo(5.0);
        assertThat(TaskValues.readFloat(0.5)).isEqualTo(0.5f);
        assertThat(TaskValues.readBoolean(true)).isTrue();
        assertThat(TaskValues.readString("eu")).isEqualTo("eu");
        assertThat(TaskValues.readDecimal(tagged("decimal", "12.50"))).isEqualTo(new BigDecimal("12.50"));
        assertThat(TaskValues.readDate("2026-01-31")).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(TaskValues.readTimestamp(tagged("timestamp", "2026-01-31T10:00:00.5Z")))
                .isEqualTo(Instant.parse("2026-01-31T10:00:00.5Z"));
        assertThat(TaskValues.readDuration(duration("-1", -500_000_000L))).isEqualTo(Duration.ofMillis(-1500));
        assertThat(TaskValues.readBytes(tagged("bytes", "SGVsbG8="))).isEqualTo("Hello".getBytes());
        assertThat(TaskValues.readEnum("silver", Tier.values())).isEqualTo(Tier.silver);
    }

    @Test
    void refusesEachScalarInAnotherForm() {
        assertRefused(() -> TaskValues.readLong(0.5), "a whole number", "of type Double");
        assertRefused(() -> TaskValues.readLong(9007199254740993.0), "a whole number",
                "9.007199254740992E15, a whole number that a double cannot hold exactly");
        assertRefused(() -> TaskValues.readLong(-9007199254740992.0), "a whole number",
                "-9.007199254740992E15, a whole number that a double cannot hold exactly");
        assertRefused(() -> TaskValues.readLong(tagged("int64", "9223372036854775808")), "a whole number",
                "the tagged int64 9223372036854775808, beyond 64 bits");
        assertRefused(() -> TaskValues.readInt(3_000_000_000L), "a whole number from -2147483648 to 2147483647",
                "3000000000");
        assertRefused(() -> TaskValues.readDouble(9_007_199_254_740_993L), "a number",
                "a Long that a double cannot hold exactly");
        assertRefused(() -> TaskValues.readFloat(1e39),
                "a number from -3.4028234663852886E38 to 3.4028234663852886E38", "1.0E39");
        assertRefused(() -> TaskValues.readBoolean("true"), "a boolean", "of type String");
        assertRefused(() -> TaskValues.readString(null), "a string", "null");
        assertRefused(() -> TaskValues.readDecimal(12.5),
                "a tagged decimal, such as {\"$bptd\": \"decimal\", \"value\": \"12.50\"}", "of type Double");
        assertRefused(() -> TaskValues.readDecimal(tagged("decimal", "1e2")),
                "a tagged decimal, such as {\"$bptd\": \"decimal\", \"value\": \"12.50\"}", "1e2");
        assertRefused(() -> TaskValues.readDate("2026-02-30"), "a date such as 2026-01-31", "2026-02-30");
        assertRefused(() -> TaskValues.readTimestamp(tagged("timestamp", "2026-01-31T10:00:00+02:00")),
                "a tagged UTC timestamp, such as {\"$bptd\": \"timestamp\", \"value\": \"2026-01-31T12:00:00Z\"}",
                "2026-01-31T10:00:00+02:00");
        assertRefused(() -> TaskValues.readDuration(duration("1", 1_000_000_000L)),
                "a tagged duration, such as {\"$bptd\": \"duration\", \"seconds\": \"90\", \"nanos\": 0}",
                "seconds 1 and nanos 1000000000");
        assertRefused(() -> TaskValues.readBytes(tagged("bytes", "not base64!")),
                "tagged bytes, such as {\"$bptd\": \"bytes\", \"value\": \"SGVsbG8=\"}", "not valid base64");
        assertRefused(() -> TaskValues.readEnum("bronze", Tier.values()), "one of [gold, silver]", "bronze");
    }

    @Test
    void namesWhereAValueIsWhenItIsRefused() {
        Map<String, Object> kwargs = Map.of("input", List.of(
                Map.of("amount", 1.5),
                Map.of("amount", "2")));

        assertThatThrownBy(() -> TaskValues.input(kwargs, rows -> TaskValues.readList(rows,
                row -> TaskValues.field(TaskValues.readRecord(row), "amount", TaskValues::readDouble))))
                .isInstanceOf(TaskValueException.class)
                .hasMessage("input[1].amount is of type String, expected a number");
        assertThatThrownBy(() -> TaskValues.parameter(Map.of(), "threshold", TaskValues::readDouble))
                .hasMessage("parameter threshold is missing, expected a value in the run's parameters");
        assertThatThrownBy(() -> TaskValues.parameter(Map.of("weights", Map.of("eu", "x")), "weights",
                value -> TaskValues.readMap(value, TaskValues::readDouble)))
                .hasMessage("parameter weights[\"eu\"] is of type String, expected a number");
        assertThatThrownBy(() -> TaskValues.field(Map.of(), "id", TaskValues::readString))
                .hasMessage(".id is missing, expected a value");
    }

    @Test
    void readsMissingInputAsNoRowsAndANullableValueAsNull() {
        assertThat(TaskValues.input(Map.of(), TaskValues::readRows)).isEmpty();
        assertThat(TaskValues.nullable(null, TaskValues::readString)).isNull();
        assertThat(TaskValues.nullable("eu", TaskValues::readString)).isEqualTo("eu");
        Rows rows = TaskValues.readRows(List.of(Map.of("id", 1L)));
        assertThat(rows).containsExactly(Row.of(Map.of("id", 1L)));
        assertRefused(() -> TaskValues.readRows(List.of("not a row")), "a row object", "of type String");
    }

    @Test
    void writesEachValueInTheFormCoreValidates() {
        assertThat(TaskValues.writeInt(7)).isEqualTo(7L);
        assertThat(TaskValues.writeDecimal(new BigDecimal("-0.00"))).isEqualTo(tagged("decimal", "0.00"));
        assertThat(TaskValues.writeDecimal(new BigDecimal("1E+3"))).isEqualTo(tagged("decimal", "1000"));
        assertThat(TaskValues.writeDate(LocalDate.of(2026, 1, 31))).isEqualTo("2026-01-31");
        assertThat(TaskValues.writeTimestamp(Instant.parse("2026-01-31T12:00:00+02:00")))
                .isEqualTo(tagged("timestamp", "2026-01-31T10:00:00Z"));
        assertThat(TaskValues.writeDuration(Duration.ofMillis(-1500))).isEqualTo(duration("-1", -500_000_000L));
        assertThat(TaskValues.writeBytes("Hello".getBytes())).isEqualTo(tagged("bytes", "SGVsbG8="));
        assertThat(TaskValues.writeEnum(Tier.gold)).isEqualTo("gold");
        assertThat(TaskValues.writeList(Arrays.asList(1, null), TaskValues::writeInt)).containsExactly(1L, null);
        assertThat(TaskValues.writeMap(Map.of("eu", 2), TaskValues::writeInt)).isEqualTo(Map.of("eu", 2L));
        assertThat(TaskValues.writeRows(Rows.of(Row.of(Map.of("id", 1L))))).containsExactly(Map.of("id", 1L));
        assertThat(TaskValues.writeDecimal(null)).isNull();
    }

    @Test
    void refusesToWriteAYearCoreCannotHoldNamingWhereItIs() {
        Map<String, Object> fields = new LinkedHashMap<>();

        assertThatThrownBy(() -> TaskValues.result(List.of(LocalDate.of(10_000, 1, 1)),
                dates -> TaskValues.writeList(dates, TaskValues::writeDate)))
                .hasMessage("result[0] is +10000-01-01, expected a value with a year from 0000 to 9999");
        assertThatThrownBy(() -> TaskValues.put(fields, "at", Instant.parse("-0001-01-01T00:00:00Z"),
                TaskValues::writeTimestamp))
                .hasMessage(".at is -0001-01-01T00:00:00Z, expected a value with a year from 0000 to 9999");
    }

    private static void assertRefused(Runnable read, String expected, String found) {
        assertThatThrownBy(read::run)
                .isInstanceOfSatisfying(TaskValueException.class, refused -> {
                    assertThat(refused.getExpected()).isEqualTo(expected);
                    assertThat(refused.getFound()).isEqualTo(found);
                });
    }

    private static Map<String, Object> tagged(String kind, String value) {
        Map<String, Object> tagged = new LinkedHashMap<>();
        tagged.put("$bptd", kind);
        tagged.put("value", value);
        return tagged;
    }

    private static Map<String, Object> duration(String seconds, long nanos) {
        Map<String, Object> duration = new LinkedHashMap<>();
        duration.put("$bptd", "duration");
        duration.put("seconds", seconds);
        duration.put("nanos", nanos);
        return duration;
    }
}

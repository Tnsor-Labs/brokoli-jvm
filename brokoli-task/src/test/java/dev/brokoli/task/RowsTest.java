package dev.brokoli.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowsTest {

    @Test
    void fromMapsAndToMapsKeepTheRowsAndTheirOrder() {
        List<Map<String, Object>> maps = List.of(Map.of("id", 1L), Map.of("id", 2L), Map.of("id", 3L));

        Rows rows = Rows.fromMaps(maps);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getLong("id")).isEqualTo(1L);
        assertThat(rows.toMaps()).isEqualTo(maps);
    }

    @Test
    void filterKeepsTheRowsThatPassInOrder() {
        Rows rows = Rows.of(row("score", 0.9), row("score", 0.1), row("score", 0.7));

        Rows kept = rows.filter(row -> row.getDouble("score") >= 0.5);

        assertThat(kept).containsExactly(row("score", 0.9), row("score", 0.7));
        assertThat(rows).hasSize(3);
    }

    @Test
    void mapChangesEveryRowInOrder() {
        Rows rows = Rows.of(row("amount", 1L), row("amount", 2L));

        Rows doubled = rows.map(row -> row.with("amount", row.getLong("amount") * 2));

        assertThat(doubled).containsExactly(row("amount", 2L), row("amount", 4L));
    }

    @Test
    void mapRefusesAChangeThatReturnsNullNamingTheRow() {
        Rows rows = Rows.of(row("amount", 1L), row("amount", 2L));

        assertThatThrownBy(() -> rows.map(row -> row.getLong("amount") == 2L ? null : row))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("change returned null for row 1");
    }

    @Test
    void rowsCannotBeAddedRemovedOrReplaced() {
        Rows rows = Rows.of(row("amount", 1L));

        assertThatThrownBy(() -> rows.add(row("amount", 2L))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.remove(0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.set(0, row("amount", 2L))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rows.toMaps().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copyOfTakesACopyAndRefusesANullRowNamingItsPosition() {
        List<Row> source = new ArrayList<>(List.of(row("amount", 1L)));
        Rows rows = Rows.copyOf(source);
        source.add(row("amount", 2L));

        assertThat(rows).hasSize(1);
        assertThatThrownBy(() -> Rows.copyOf(Arrays.asList(row("amount", 1L), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("row 1");
    }

    @Test
    void fromMapsRefusesANullMapNamingItsPosition() {
        assertThatThrownBy(() -> Rows.fromMaps(Arrays.asList(Map.of("id", 1L), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("row 1");
    }

    @Test
    void toRowsEndsAStreamInRowsKeepingTheOrder() {
        Rows rows = Rows.of(row("year", 2024L), row("year", 2025L), row("year", 2026L));

        Rows recent = rows.stream()
                .filter(row -> row.getLong("year") >= 2025)
                .collect(Rows.toRows());

        assertThat(recent).containsExactly(row("year", 2025L), row("year", 2026L));
        assertThatThrownBy(() -> recent.add(row("year", 2027L))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toRowsKeepsTheOrderOfAParallelStream() {
        List<Row> source = IntStream.range(0, 1000).mapToObj(i -> row("i", (long) i)).toList();

        Rows collected = source.parallelStream().collect(Rows.toRows());

        assertThat(collected).containsExactlyElementsOf(source);
    }

    @Test
    void toRowsRefusesANullRowNamingItsPosition() {
        assertThatThrownBy(() -> Stream.of(row("i", 0L), null).collect(Rows.toRows()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("row 1");
    }

    @Test
    void mapAcceptsAFunctionWrittenForAnyObject() {
        Function<Object, Row> constant = ignored -> row("amount", 0L);

        Rows zeroed = Rows.of(row("amount", 1L), row("amount", 2L)).map(constant);

        assertThat(zeroed).containsExactly(row("amount", 0L), row("amount", 0L));
    }

    @Test
    void emptyHasNoRows() {
        assertThat(Rows.empty()).isEmpty();
        assertThat(Rows.empty().toMaps()).isEmpty();
    }

    private static Row row(String column, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, value);
        return Row.of(values);
    }
}

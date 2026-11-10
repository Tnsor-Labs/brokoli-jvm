package dev.brokoli.task;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * The rows of a dataset whose schema the task does not declare, in order.
 *
 * <p>A task that takes or returns {@code Rows} leaves its row schema undeclared; to declare one,
 * take or return a {@code List} of a Java record instead. {@code Rows} is an unmodifiable
 * {@code List}: adding, removing or replacing rows throws {@link UnsupportedOperationException}.
 * {@link #filter(Predicate)} and {@link #map(Function)} return new rows, and a stream of rows
 * ends in {@code Rows} with {@link #toRows()}:
 *
 * <pre>{@code
 * Rows recent = input.stream()
 *         .filter(row -> row.getLong("year") >= 2025)
 *         .collect(Rows.toRows());
 * }</pre>
 */
public final class Rows extends AbstractList<Row> implements RandomAccess {

    private static final Rows EMPTY = new Rows(List.of());

    private final List<Row> rows;

    private Rows(List<Row> rows) {
        this.rows = rows;
    }

    /**
     * No rows.
     *
     * @return the empty rows
     */
    public static Rows empty() {
        return EMPTY;
    }

    /**
     * The given rows, in order.
     *
     * @param rows the rows
     * @return the rows
     * @throws NullPointerException if {@code rows} or one of its rows is null; the message names
     *         the row's position
     */
    public static Rows of(Row... rows) {
        Objects.requireNonNull(rows, "rows");
        return copyOf(Arrays.asList(rows));
    }

    /**
     * A copy of the given rows, in iteration order.
     *
     * @param rows the rows
     * @return the rows
     * @throws NullPointerException if {@code rows} or one of its rows is null; the message names
     *         the row's position
     */
    public static Rows copyOf(Collection<Row> rows) {
        Objects.requireNonNull(rows, "rows");
        return owning(new ArrayList<>(rows));
    }

    /**
     * Rows made from maps of column name to value, such as the rows a task receives as JSON.
     *
     * @param maps the rows as maps, in order
     * @return the rows
     * @throws NullPointerException if {@code maps}, one of its maps, or a column name is null
     * @throws RowValueException if a map holds a value that is not a JSON value
     */
    public static Rows fromMaps(List<? extends Map<String, ?>> maps) {
        Objects.requireNonNull(maps, "maps");
        List<Row> rows = new ArrayList<>(maps.size());
        for (Map<String, ?> map : maps) {
            int position = rows.size();
            rows.add(Row.of(Objects.requireNonNull(map, () -> "row " + position)));
        }
        return new Rows(Collections.unmodifiableList(rows));
    }

    /**
     * A collector that gathers a stream of rows into {@code Rows}, in encounter order.
     *
     * @return the collector
     * @throws NullPointerException when the collected stream holds a null row; the message names
     *         the row's position
     */
    public static Collector<Row, ?, Rows> toRows() {
        return Collector.of(
                ArrayList<Row>::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                Rows::owning);
    }

    /**
     * The rows as maps of column name to value, in order.
     *
     * @return the rows as unmodifiable maps
     */
    public List<Map<String, Object>> toMaps() {
        List<Map<String, Object>> maps = new ArrayList<>(rows.size());
        for (Row row : rows) {
            maps.add(row.toMap());
        }
        return Collections.unmodifiableList(maps);
    }

    /**
     * The rows that pass the test, in order.
     *
     * @param keep the test a row must pass to be kept
     * @return the kept rows
     */
    public Rows filter(Predicate<? super Row> keep) {
        Objects.requireNonNull(keep, "keep");
        List<Row> kept = new ArrayList<>();
        for (Row row : rows) {
            if (keep.test(row)) {
                kept.add(row);
            }
        }
        return new Rows(Collections.unmodifiableList(kept));
    }

    /**
     * Each row changed by the function, in order.
     *
     * @param change the function that returns the changed row
     * @return the changed rows
     * @throws NullPointerException if {@code change} returns null; the message names the row's
     *         position
     */
    public Rows map(Function<? super Row, ? extends Row> change) {
        Objects.requireNonNull(change, "change");
        List<Row> changed = new ArrayList<>(rows.size());
        for (Row row : rows) {
            int position = changed.size();
            changed.add(Objects.requireNonNull(change.apply(row), () -> "change returned null for row " + position));
        }
        return new Rows(Collections.unmodifiableList(changed));
    }

    /**
     * The row at the position.
     *
     * @param index the position, from zero
     * @return the row
     * @throws IndexOutOfBoundsException if there is no row at {@code index}
     */
    @Override
    public Row get(int index) {
        return rows.get(index);
    }

    /**
     * The number of rows.
     *
     * @return the number of rows
     */
    @Override
    public int size() {
        return rows.size();
    }

    private static Rows owning(List<Row> rows) {
        for (int position = 0; position < rows.size(); position++) {
            int named = position;
            Objects.requireNonNull(rows.get(position), () -> "row " + named);
        }
        return new Rows(Collections.unmodifiableList(rows));
    }
}

package dev.brokoli.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class RowsBenchmark {

    private static final String CUSTOMER_PREFIX = "customer-";
    private static final int CUSTOMERS = 1000;
    private static final long FIRST_PLACED = 1_700_000_000L;
    private static final double HALF = 0.5;
    private static final String CENTS = ".25";

    @Param("100000")
    public int rows;

    private Map<String, Object> kwargs;

    @Setup
    public void stageInput() {
        kwargs = Map.of(Orders.INPUT, orders(rows));
    }

    @Benchmark
    public Object records() {
        return Orders_BrokoliTasks.paidOrders(kwargs);
    }

    @Benchmark
    public Object rows() {
        return Orders_BrokoliTasks.paidRows(kwargs);
    }

    @Benchmark
    public Object rawMap() {
        return Orders.paidMaps(kwargs);
    }

    @Benchmark
    public Object decodedMap() {
        return Orders.paidMapsDecoded(kwargs);
    }

    static List<Map<String, Object>> orders(int count) {
        List<Map<String, Object>> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put(Orders.ID, (long) i);
            order.put(Orders.CUSTOMER, CUSTOMER_PREFIX + i % CUSTOMERS);
            order.put(Orders.AMOUNT, i + HALF);
            order.put(Orders.PAID, i % 2 == 0);
            order.put(Orders.TOTAL, Map.of(Orders.TAG, Orders.DECIMAL, Orders.VALUE, i + CENTS));
            order.put(Orders.PLACED, Map.of(Orders.TAG, Orders.TIMESTAMP, Orders.VALUE,
                    Instant.ofEpochSecond(FIRST_PLACED + i).toString()));
            orders.add(order);
        }
        return orders;
    }
}

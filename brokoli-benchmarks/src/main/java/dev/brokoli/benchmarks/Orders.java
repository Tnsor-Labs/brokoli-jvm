package dev.brokoli.benchmarks;

import dev.brokoli.task.Rows;
import dev.brokoli.task.Task;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Orders {

    static final String INPUT = "input";
    static final String ID = "id";
    static final String CUSTOMER = "customer";
    static final String AMOUNT = "amount";
    static final String PAID = "paid";
    static final String TOTAL = "total";
    static final String PLACED = "placed";
    static final String TAG = "$bptd";
    static final String VALUE = "value";
    static final String DECIMAL = "decimal";
    static final String TIMESTAMP = "timestamp";

    public record Order(long id, String customer, double amount, boolean paid, BigDecimal total, Instant placed) {
    }

    private Orders() {
    }

    @Task
    public static List<Order> paidOrders(List<Order> input) {
        return input.stream().filter(Order::paid).toList();
    }

    @Task
    public static Rows paidRows(Rows input) {
        return input.filter(row -> row.getBoolean(PAID));
    }

    public static Object paidMaps(Map<String, Object> kwargs) {
        List<Object> paid = new ArrayList<>();
        for (Object row : (List<?>) kwargs.get(INPUT)) {
            if (Boolean.TRUE.equals(((Map<?, ?>) row).get(PAID))) {
                paid.add(row);
            }
        }
        return paid;
    }

    public static Object paidMapsDecoded(Map<String, Object> kwargs) {
        List<Object> paid = new ArrayList<>();
        for (Object value : (List<?>) kwargs.get(INPUT)) {
            Map<?, ?> row = (Map<?, ?>) value;
            long id = ((Number) row.get(ID)).longValue();
            String customer = (String) row.get(CUSTOMER);
            double amount = ((Number) row.get(AMOUNT)).doubleValue();
            boolean isPaid = (Boolean) row.get(PAID);
            BigDecimal total = new BigDecimal((String) ((Map<?, ?>) row.get(TOTAL)).get(VALUE));
            Instant placed = Instant.parse((String) ((Map<?, ?>) row.get(PLACED)).get(VALUE));
            if (isPaid) {
                Map<String, Object> order = new LinkedHashMap<>();
                order.put(ID, id);
                order.put(CUSTOMER, customer);
                order.put(AMOUNT, amount);
                order.put(PAID, true);
                order.put(TOTAL, tagged(DECIMAL, total.toPlainString()));
                order.put(PLACED, tagged(TIMESTAMP, placed.toString()));
                paid.add(order);
            }
        }
        return paid;
    }

    private static Map<String, Object> tagged(String type, String text) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(TAG, type);
        value.put(VALUE, text);
        return value;
    }
}

package dev.brokoli.benchmarks;

import dev.brokoli.task.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Lines {

    static final String INPUT = "input";
    static final String ID = "id";
    static final String CUSTOMER = "customer";
    static final String AMOUNT = "amount";
    static final String PAID = "paid";
    static final String REGION = "region";
    static final String QUANTITY = "quantity";

    private static final String CUSTOMER_PREFIX = "customer-";
    private static final int CUSTOMERS = 1000;
    private static final List<String> REGIONS = List.of("north", "south", "east", "west");
    private static final double HALF = 0.5;
    private static final int QUANTITIES = 10;

    public record Line(long id, String customer, double amount, boolean paid, String region, long quantity) {
    }

    private Lines() {
    }

    @Task
    public static List<Line> paidLines(List<Line> input) {
        return input.stream().filter(Line::paid).toList();
    }

    public static Object paidLineMaps(Map<String, Object> kwargs) {
        List<Object> paid = new ArrayList<>();
        Object input = kwargs.get(INPUT);
        if (input == null) {
            return paid;
        }
        for (Object row : (List<?>) input) {
            if (Boolean.TRUE.equals(((Map<?, ?>) row).get(PAID))) {
                paid.add(row);
            }
        }
        return paid;
    }

    static List<Map<String, Object>> generate(int count) {
        List<Map<String, Object>> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put(ID, (long) i);
            line.put(CUSTOMER, CUSTOMER_PREFIX + i % CUSTOMERS);
            line.put(AMOUNT, i + HALF);
            line.put(PAID, i % 2 == 0);
            line.put(REGION, REGIONS.get(i % REGIONS.size()));
            line.put(QUANTITY, (long) (i % QUANTITIES + 1));
            lines.add(line);
        }
        return lines;
    }
}

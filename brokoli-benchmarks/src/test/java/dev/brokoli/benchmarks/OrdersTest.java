package dev.brokoli.benchmarks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrdersTest {

    private static final Map<String, Object> KWARGS = Map.of(Orders.INPUT, RowsBenchmark.orders(10));

    @Test
    void theHandDecodedTaskReturnsWhatTheEntryMethodForRecordsReturns() {
        assertThat(Orders.paidMapsDecoded(KWARGS)).isEqualTo(Orders_BrokoliTasks.paidOrders(KWARGS));
    }

    @Test
    void theRawMapTaskReturnsWhatTheEntryMethodForRowsReturns() {
        assertThat(Orders.paidMaps(KWARGS)).isEqualTo(Orders_BrokoliTasks.paidRows(KWARGS));
    }

    @Test
    void everyTaskKeepsOnlyThePaidOrders() {
        assertThat((List<?>) Orders.paidMaps(KWARGS)).hasSize(5)
                .allSatisfy(order -> assertThat(((Map<?, ?>) order).get(Orders.PAID)).isEqualTo(true));
    }
}

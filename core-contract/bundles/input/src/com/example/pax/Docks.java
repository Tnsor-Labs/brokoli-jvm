package com.example.pax;

import dev.brokoli.task.Rows;
import dev.brokoli.task.Task;

public final class Docks {

    public static final class InboundDeliveryWindowPriorityQueueRebalancingStrategyForOverbookedLoadingDocksDuringPeakHours {

        @Task
        public static Rows rebalance(Rows input) {
            return input;
        }
    }
}

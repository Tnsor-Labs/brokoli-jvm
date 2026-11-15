package dev.brokoli.pipeline;

import java.time.ZoneId;
import java.time.ZoneOffset;

final class TimeZones {

    static final String UTC = "UTC";

    private TimeZones() {
    }

    static String irId(String what, ZoneId zone) {
        if (zone.equals(ZoneOffset.UTC)) {
            return UTC;
        }
        if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
            throw new IllegalArgumentException(what + " " + zone + " is not an IANA time zone, which the server"
                    + " takes, such as Africa/Maputo or UTC");
        }
        return zone.getId();
    }
}

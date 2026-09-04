package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ReservationStatus {
    PENDING, COMPLETED, EXPIRED_ALT_LINK_SENT, ESCALATED;
    @JsonValue public String toJson() { return name().toLowerCase(Locale.ROOT); }
}

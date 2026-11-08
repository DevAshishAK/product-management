package com.pcom.common.dto.event;

import java.util.List;

public final class Topics {
    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String INVOICE_EVENTS = "invoice-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";

    public static final List<String> ALL =
            List.of(ORDER_EVENTS, INVENTORY_EVENTS, PAYMENT_EVENTS, INVOICE_EVENTS, NOTIFICATION_EVENTS);

    private Topics() {}
}

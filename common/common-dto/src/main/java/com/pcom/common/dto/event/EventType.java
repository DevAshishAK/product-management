package com.pcom.common.dto.event;

/** Every domain event, the topic it is published to and its current schema version. */
public enum EventType {
    ORDER_CREATED(Topics.ORDER_EVENTS, 1),
    ORDER_CONFIRMED(Topics.ORDER_EVENTS, 1),
    ORDER_CANCELLED(Topics.ORDER_EVENTS, 1),
    INVENTORY_RESERVED(Topics.INVENTORY_EVENTS, 1),
    INVENTORY_RESERVATION_FAILED(Topics.INVENTORY_EVENTS, 1),
    INVENTORY_RELEASED(Topics.INVENTORY_EVENTS, 1),
    INVENTORY_ADJUSTED(Topics.INVENTORY_EVENTS, 1),
    PAYMENT_INITIATED(Topics.PAYMENT_EVENTS, 1),
    PAYMENT_COMPLETED(Topics.PAYMENT_EVENTS, 1),
    PAYMENT_FAILED(Topics.PAYMENT_EVENTS, 1),
    PAYMENT_REFUNDED(Topics.PAYMENT_EVENTS, 1),
    INVOICE_GENERATED(Topics.INVOICE_EVENTS, 1),
    NOTIFICATION_TRIGGERED(Topics.NOTIFICATION_EVENTS, 1);

    private final String topic;
    private final int version;

    EventType(String topic, int version) {
        this.topic = topic;
        this.version = version;
    }

    public String topic() { return topic; }

    public int version() { return version; }
}

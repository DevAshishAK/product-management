package com.pcom.notification.service;

public enum NotificationTemplate {
    ORDER_CONFIRMED("Order confirmed", "Hi %s, your order %s has been confirmed."),
    ORDER_CANCELLED("Order cancelled", "Hi %s, your order %s was cancelled. Reason: %s"),
    PAYMENT_COMPLETED("Payment received", "Hi %s, we received your payment for order %s."),
    PAYMENT_FAILED("Payment failed", "Hi %s, the payment for order %s failed. Reason: %s"),
    PAYMENT_REFUNDED("Refund processed", "Hi %s, a refund for order %s has been issued."),
    INVOICE_READY("Your invoice is ready", "Hi %s, invoice %s for order %s is ready to download.");

    private final String subject;
    private final String body;

    NotificationTemplate(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public String subject() { return subject; }

    public String body(Object... args) {
        // templates may use fewer arguments than supplied
        int needed = (int) body.chars().filter(c -> c == '%').count();
        Object[] used = java.util.Arrays.copyOf(args, Math.min(needed, args.length));
        return String.format(body, used);
    }
}

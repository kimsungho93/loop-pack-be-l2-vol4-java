package com.loopers.payment.application.event;

import com.loopers.order.domain.OrderItem;

public record OrderPaidItemSnapshot(
    Long productId,
    long unitPrice,
    int quantity,
    long totalPrice
) {

    public static OrderPaidItemSnapshot from(OrderItem item) {
        return new OrderPaidItemSnapshot(
            item.getProductId(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getTotalPrice()
        );
    }
}
